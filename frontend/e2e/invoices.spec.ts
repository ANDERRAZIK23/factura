import { readFile } from 'node:fs/promises';
import { expect, request, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8080/api/v1/';
const adminEmail = process.env.INITIAL_ADMIN_EMAIL ?? 'admin@example.com';
const password = process.env.INITIAL_ADMIN_PASSWORD ?? 'AdminPassword123!';
const runId = Date.now().toString();
const managerEmail = `e2e-invoice-manager-${runId}@example.com`;
const viewerEmail = `e2e-invoice-viewer-${runId}@example.com`;
const clientName = `Invoice Client ${runId}`;
const firstProductName = `Invoice Service ${runId}`;
const secondProductName = `Invoice Supplies ${runId}`;
let clientId = '';
let firstProductId = '';
let secondProductId = '';
let validatedInvoiceId = '';
let validatedInvoiceNumber = '';

test.describe.configure({ mode: 'serial' });

async function authenticatedApi(email = adminEmail) {
  const api = await request.newContext({ baseURL: apiBase });
  const response = await api.post('auth/login', { data: { email, password } });
  expect(response.ok()).toBeTruthy();
  const { accessToken } = await response.json();
  return { api, headers: { Authorization: `Bearer ${accessToken}` } };
}

async function login(page: Page, email = adminEmail) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function createClient(page: Page) {
  await page.getByRole('link', { name: 'Clients' }).click();
  await page.getByTestId('create-client').click();
  await page.getByLabel(/Client name/).fill(clientName);
  await page.getByLabel('Phone number').fill('+212 522 00 00 00');
  await page.getByLabel('Address').fill('Casablanca, Morocco');
  await page.getByLabel('Email').fill(`invoice-client-${runId}@example.com`);
  await page.getByLabel('ICE').fill(runId.slice(-15).padStart(15, '0'));
  await page.getByTestId('client-submit').click();
  await expect(page.getByText('Client created successfully.')).toBeVisible();
  clientId = new URL(page.url()).pathname.split('/').at(-1)!;
  expect(clientId).not.toBe('');
}

async function createProduct(page: Page, name: string, price: string, vat: string, unit: string) {
  await page.getByRole('link', { name: 'Products' }).click();
  await page.getByTestId('create-product').click();
  await page.getByLabel(/Product name/).fill(name);
  await page.getByLabel('Description').fill('Created by the Phase 10 invoice workflow');
  await page.getByLabel('Unit price excluding tax *').fill(price);
  await page.getByLabel('Default VAT rate (%) *').fill(vat);
  await page.getByLabel('Unit *').selectOption(unit);
  await page.getByTestId('product-submit').click();
  await expect(page.getByText('Product created successfully.')).toBeVisible();
  return new URL(page.url()).pathname.split('/').at(-1)!;
}

async function getInvoice(api: APIRequestContext, headers: Record<string, string>, id: string) {
  const response = await api.get(`invoices/${id}`, { headers });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test.beforeAll(async () => {
  const { api, headers } = await authenticatedApi();
  for (const user of [
    { fullName: 'Invoice E2E Manager', email: managerEmail, password, role: 'INVOICE_MANAGER' },
    { fullName: 'Invoice E2E Viewer', email: viewerEmail, password, role: 'VIEWER' },
  ]) {
    const response = await api.post('users', { headers, data: user });
    expect(response.status()).toBe(201);
  }
  await api.dispose();
});

test('ADMIN completes the full invoice workflow and downloads the generated PDF', async ({ page }) => {
  await login(page);
  await createClient(page);
  firstProductId = await createProduct(page, firstProductName, '120', '20', 'SERVICE');
  secondProductId = await createProduct(page, secondProductName, '35', '10', 'PIECE');

  await page.getByRole('link', { name: 'Invoices' }).click();
  await page.getByTestId('create-invoice').click();
  await page.getByLabel('Invoice date *').fill('2026-08-04');
  await page.getByLabel('Client *').selectOption(clientId);
  await page.getByLabel('Payment method *').selectOption('CHECK');
  await page.getByLabel('Customer order reference').fill(`PO-${runId}`);

  await page.getByTestId('add-invoice-line').click();
  await page.getByLabel('Product 1').selectOption(firstProductId);
  await page.getByLabel('Quantity 1').fill('2');
  await expect(page.getByLabel('Unit price 1')).toHaveValue('120.00');
  await expect(page.getByLabel('VAT rate 1')).toHaveValue('20.00');
  await page.getByLabel('Discount rate 1').fill('5');
  await expect(page.getByLabel('Unit price 1')).not.toHaveAttribute('readonly');
  await expect(page.getByTestId('invoice-line-total-0')).toContainText('273.60 MAD');

  await page.getByTestId('add-invoice-line').click();
  await page.getByLabel('Product 2').selectOption(secondProductId);
  await page.getByLabel('Quantity 2').fill('3');
  await page.getByLabel('Discount rate 2').fill('10');
  await expect(page.getByTestId('invoice-line-total-1')).toContainText('103.95 MAD');
  await expect(page.getByLabel('Invoice totals preview')).toContainText('377.55 MAD');
  await page.getByTestId('invoice-submit').click();
  await expect(page.getByText('Draft invoice created successfully.')).toBeVisible();
  validatedInvoiceId = new URL(page.url()).pathname.split('/').at(-1)!;
  await expect(page.getByText('DRAFT')).toBeVisible();

  await page.getByTestId('validate-invoice').click();
  await expect(page.getByText(/assigns the final invoice number/)).toBeVisible();
  await page.getByTestId('confirm-invoice-action').click();
  const invoiceNumberLocator = page.getByText(/^FA-[0-9]{4}-[0-9]{6,}$/);
  await expect(invoiceNumberLocator).toHaveCount(1);
  validatedInvoiceNumber = (await invoiceNumberLocator.textContent())!;
  await expect(page.getByText('VALIDATED')).toBeVisible();
  await expect(page.getByText('377,55 MAD')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit invoice' })).toHaveCount(0);

  const downloadPromise = page.waitForEvent('download');
  await page.getByTestId('download-invoice-pdf').click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(`${validatedInvoiceNumber}.pdf`);
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const bytes = await readFile(downloadPath!);
  expect(bytes.subarray(0, 5).toString('ascii')).toBe('%PDF-');
  expect(bytes.length).toBeGreaterThan(500);

  await page.getByText('← Back to invoices').click();
  await page.getByLabel('Search invoices').fill(validatedInvoiceNumber);
  await expect(page.getByRole('link', { name: validatedInvoiceNumber })).toBeVisible();
  await page.getByRole('link', { name: validatedInvoiceNumber }).click();
  await page.goto(`/invoices/${validatedInvoiceId}/edit`);
  await expect(page).toHaveURL(new RegExp(`/invoices/${validatedInvoiceId}$`));
  await expect(page.getByTestId('invoice-details-page')).toBeVisible();
});

test('INVOICE_MANAGER sees validation errors, then cancels the empty draft', async ({ page }) => {
  await login(page, managerEmail);
  await page.getByRole('link', { name: 'Invoices' }).click();
  await page.getByTestId('create-invoice').click();
  await page.getByLabel('Invoice date *').fill('2026-08-04');
  await page.getByLabel('Client *').selectOption(clientId);
  await page.getByTestId('invoice-submit').click();
  await expect(page.getByText('Draft invoice created successfully.')).toBeVisible();
  await page.getByTestId('validate-invoice').click();
  await page.getByTestId('confirm-invoice-action').click();
  await expect(page.getByText('An invoice must contain at least one line')).toBeVisible();
  await expect(page.getByText('DRAFT')).toBeVisible();
  await page.getByTestId('cancel-invoice').click();
  await page.getByTestId('confirm-invoice-action').click();
  await expect(page.getByText('CANCELLED')).toBeVisible();
});

test('VIEWER can inspect and download but cannot mutate invoices', async ({ page }) => {
  await login(page, viewerEmail);
  await page.getByRole('link', { name: 'Invoices' }).click();
  await expect(page.getByTestId('create-invoice')).toHaveCount(0);
  await page.goto(`/invoices/${validatedInvoiceId}`);
  await expect(page.getByTestId('invoice-details-page')).toBeVisible();
  await expect(page.getByTestId('validate-invoice')).toHaveCount(0);
  await expect(page.getByTestId('mark-invoice-paid')).toHaveCount(0);
  await expect(page.getByTestId('cancel-invoice')).toHaveCount(0);
  await expect(page.getByTestId('download-invoice-pdf')).toBeVisible();
  await page.goto('/invoices/new');
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();

  const { api, headers } = await authenticatedApi(viewerEmail);
  const current = await getInvoice(api, headers, validatedInvoiceId);
  const forbidden = await api.post(`invoices/${validatedInvoiceId}/mark-paid`, { headers, data: { version: current.version } });
  expect(forbidden.status()).toBe(403);
  await api.dispose();
});

test('ADMIN marks the validated invoice as paid and the final state is immutable', async ({ page }) => {
  await login(page);
  await page.goto(`/invoices/${validatedInvoiceId}`);
  await page.getByTestId('mark-invoice-paid').click();
  await page.getByTestId('confirm-invoice-action').click();
  await expect(page.getByText('PAID')).toBeVisible();
  await expect(page.getByTestId('mark-invoice-paid')).toHaveCount(0);
  await expect(page.getByTestId('cancel-invoice')).toHaveCount(0);
  await expect(page.getByTestId('download-invoice-pdf')).toBeVisible();

  const { api, headers } = await authenticatedApi();
  const paid = await getInvoice(api, headers, validatedInvoiceId);
  expect(paid.status).toBe('PAID');
  expect(paid.invoiceNumber).toBe(validatedInvoiceNumber);
  expect(paid.pdfAvailable).toBe(true);
  expect(paid.lines).toHaveLength(2);
  const forbidden = await api.put(`invoices/${validatedInvoiceId}`, { headers, data: { invoiceDate: paid.invoiceDate, clientId, paymentMethod: 'CASH', lines: [], version: paid.version } });
  expect(forbidden.status()).toBe(409);
  await api.dispose();
});

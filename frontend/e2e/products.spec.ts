import { expect, request, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8080/api/v1/';
const adminEmail = process.env.INITIAL_ADMIN_EMAIL ?? 'admin@example.com';
const password = process.env.INITIAL_ADMIN_PASSWORD ?? 'AdminPassword123!';
const managerEmail = 'e2e-product-manager@example.com';
const viewerEmail = 'e2e-product-viewer@example.com';
const runId = Date.now().toString();
let viewerProductId = '';

test.describe.configure({ mode: 'serial' });

test.beforeAll(async () => {
  const api = await request.newContext({ baseURL: apiBase });
  const login = await api.post('auth/login', { data: { email: adminEmail, password } }); expect(login.ok()).toBeTruthy(); const { accessToken } = await login.json();
  for (const user of [{ fullName: 'Product Manager', email: managerEmail, password, role: 'INVOICE_MANAGER' }, { fullName: 'Product Viewer', email: viewerEmail, password, role: 'VIEWER' }]) {
    const response = await api.post('users', { headers: { Authorization: `Bearer ${accessToken}` }, data: user }); expect([201, 409]).toContain(response.status());
  }
  const created = await api.post('products', { headers: { Authorization: `Bearer ${accessToken}` }, data: { productName: `Viewer Product ${runId}`, description: 'Viewer reference', unitPriceExcludingTax: 25, defaultVatRate: 20, unit: 'PIECE' } });
  expect(created.status()).toBe(201); viewerProductId = (await created.json()).id; await api.dispose();
});

async function login(page: import('@playwright/test').Page, email: string) {
  await page.goto('/login'); await page.getByLabel('Email').fill(email); await page.getByLabel('Password').fill(password); await page.getByTestId('login-submit').click(); await expect(page).toHaveURL(/\/dashboard$/);
}

async function createThroughUi(page: import('@playwright/test').Page, name: string, price = '13.005', vat = '19.995', unit = 'ROLL') {
  await page.getByRole('link', { name: 'Products' }).click(); await page.getByTestId('create-product').click(); await page.getByLabel(/Product name/).fill(name); await page.getByLabel('Description').fill('E2E catalogue product');
  await page.getByLabel('Unit price excluding tax *').fill(price); await page.getByLabel('Default VAT rate (%) *').fill(vat); await page.getByLabel('Unit *').selectOption(unit); await page.getByTestId('product-submit').click();
}

test('ADMIN completes product creation, filtering, editing and status lifecycle', async ({ page }) => {
  const name = `Admin Product ${runId}`; await login(page, adminEmail); await createThroughUi(page, name);
  await expect(page.getByText('Product created successfully.')).toBeVisible(); await expect(page.getByText(/^PRD-[0-9]{6,}$/).first()).toBeVisible(); await expect(page.getByText('13.01')).toBeVisible(); await expect(page.getByText('20.00%')).toBeVisible(); await expect(page.getByText('15.61')).toBeVisible();
  const code = await page.getByText(/^PRD-[0-9]{6,}$/).first().textContent(); await page.getByText('← Back to products').click(); await page.getByLabel('Search products').fill(name); await expect(page.getByText(name)).toBeVisible(); await page.getByLabel('Unit').selectOption('ROLL'); await expect(page.getByText(code ?? '')).toBeVisible();
  await page.getByRole('link', { name: code ?? '' }).click(); await page.getByRole('link', { name: 'Edit product' }).click(); await page.getByLabel(/Product name/).fill(`${name} Updated`); await page.getByTestId('product-submit').click(); await expect(page.getByRole('heading', { name: `${name} Updated` })).toBeVisible();
  await page.getByTestId('details-product-status').click(); await page.getByTestId('confirm-product-status').click(); await expect(page.getByText('Inactive').first()).toBeVisible(); await page.getByTestId('details-product-status').click(); await page.getByTestId('confirm-product-status').click(); await expect(page.getByText('Active').first()).toBeVisible();
});

test('INVOICE_MANAGER creates, edits and deactivates a product', async ({ page }) => {
  const name = `Manager Product ${runId}`; await login(page, managerEmail); await createThroughUi(page, name, '10', '0', 'SERVICE'); await expect(page.getByText('Product created successfully.')).toBeVisible();
  await page.getByRole('link', { name: 'Edit product' }).click(); await page.getByLabel('Unit price excluding tax *').fill('11.555'); await page.getByTestId('product-submit').click(); await expect(page.getByText('11.56').first()).toBeVisible(); await page.getByTestId('details-product-status').click(); await page.getByTestId('confirm-product-status').click(); await expect(page.getByText('Inactive').first()).toBeVisible();
});

test('VIEWER has read-only UI and backend permissions', async ({ page }) => {
  await login(page, viewerEmail); await page.getByRole('link', { name: 'Products' }).click(); await expect(page.getByTestId('create-product')).toHaveCount(0); await page.goto(`/products/${viewerProductId}`); await expect(page.getByTestId('product-details-page')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit product' })).toHaveCount(0); await expect(page.getByTestId('details-product-status')).toHaveCount(0); await page.goto('/products/new'); await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
  const api = await request.newContext({ baseURL: apiBase }); const auth = await api.post('auth/login', { data: { email: viewerEmail, password } }); const { accessToken } = await auth.json(); const forbidden = await api.post('products', { headers: { Authorization: `Bearer ${accessToken}` }, data: { productName: 'Forbidden', unitPriceExcludingTax: 1, defaultVatRate: 0, unit: 'PIECE' } }); expect(forbidden.status()).toBe(403); await api.dispose();
});

test('invalid financial values are rejected without creating a product', async ({ page }) => {
  const name = `Invalid Product ${runId}`; await login(page, adminEmail); await page.getByRole('link', { name: 'Products' }).click(); await page.getByTestId('create-product').click(); await page.getByLabel(/Product name/).fill(name);
  await page.getByLabel('Unit price excluding tax *').fill('-1'); await page.getByLabel('Default VAT rate (%) *').fill('101'); await page.getByTestId('product-submit').click(); await expect(page.getByText('Unit price excluding tax must be zero or greater')).toBeVisible(); await expect(page.getByText('Default VAT rate must be between 0 and 100')).toBeVisible();
  await page.getByText('← Back to products').click(); await page.getByLabel('Search products').fill(name); await expect(page.getByText('No products found')).toBeVisible();
});

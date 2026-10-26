import { expect, request, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8080/api/v1/';
const adminEmail = process.env.INITIAL_ADMIN_EMAIL ?? 'admin@example.com';
const password = process.env.INITIAL_ADMIN_PASSWORD ?? 'AdminPassword123!';
const managerEmail = 'e2e-manager@example.com';
const viewerEmail = 'e2e-viewer@example.com';
const runId = Date.now().toString();
const ice = (suffix: number) => `${runId}${suffix.toString().padStart(2, '0')}`.slice(-15);
let viewerClientId = '';

test.describe.configure({ mode: 'serial' });

test.beforeAll(async () => {
  const api = await request.newContext({ baseURL: apiBase });
  const login = await api.post('auth/login', { data: { email: adminEmail, password } });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json();
  for (const user of [
    { fullName: 'E2E Manager', email: managerEmail, password, role: 'INVOICE_MANAGER' },
    { fullName: 'E2E Viewer', email: viewerEmail, password, role: 'VIEWER' },
  ]) {
    const response = await api.post('users', { headers: { Authorization: `Bearer ${accessToken}` }, data: user });
    expect([201, 409]).toContain(response.status());
  }
  const client = await api.post('clients', { headers: { Authorization: `Bearer ${accessToken}` }, data: { clientName: `Viewer Client ${runId}`, ice: ice(1) } });
  expect(client.status()).toBe(201);
  viewerClientId = (await client.json()).id;
  await api.dispose();
});

async function login(page: import('@playwright/test').Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function createThroughUi(page: import('@playwright/test').Page, name: string, clientIce?: string) {
  await page.getByRole('link', { name: 'Clients' }).click();
  await page.getByTestId('create-client').click();
  await page.getByLabel(/Client name/).fill(name);
  await page.getByLabel('Phone number').fill('+212 5 00 00 00');
  await page.getByLabel('Address').fill('Casablanca, Morocco');
  await page.getByLabel('Email').fill(`${name.toLowerCase().replaceAll(' ', '.')}@example.com`);
  if (clientIce) await page.getByLabel('ICE').fill(clientIce);
  await page.getByTestId('client-submit').click();
}

test('ADMIN completes the full client lifecycle', async ({ page }) => {
  const name = `Admin Client ${runId}`;
  await login(page, adminEmail);
  await createThroughUi(page, name, ice(2));
  await expect(page.getByText('Client created successfully.')).toBeVisible();
  await expect(page.getByText(/^CLI-[0-9]{6}$/).first()).toBeVisible();
  const code = await page.getByText(/^CLI-[0-9]{6}$/).first().textContent();

  await page.getByText('← Back to clients').click();
  await page.getByLabel('Search clients').fill(name);
  await expect(page.getByText(name)).toBeVisible();
  await page.getByRole('link', { name: code ?? '' }).click();
  await page.getByRole('link', { name: 'Edit client' }).click();
  await page.getByLabel(/Client name/).fill(`${name} Updated`);
  await page.getByTestId('client-submit').click();
  await expect(page.getByRole('heading', { name: `${name} Updated` })).toBeVisible();

  await page.getByTestId('details-status').click();
  await page.getByTestId('confirm-status').click();
  await expect(page.getByText('Inactive').first()).toBeVisible();
  await page.getByTestId('details-status').click();
  await page.getByTestId('confirm-status').click();
  await expect(page.getByText('Active').first()).toBeVisible();
});

test('INVOICE_MANAGER creates, edits and changes client status', async ({ page }) => {
  const name = `Manager Client ${runId}`;
  await login(page, managerEmail);
  await createThroughUi(page, name, ice(3));
  await expect(page.getByText('Client created successfully.')).toBeVisible();
  await page.getByRole('link', { name: 'Edit client' }).click();
  await page.getByLabel(/Client name/).fill(`${name} Updated`);
  await page.getByTestId('client-submit').click();
  await expect(page.getByRole('heading', { name: `${name} Updated` })).toBeVisible();
  await page.getByTestId('details-status').click();
  await page.getByTestId('confirm-status').click();
  await expect(page.getByText('Inactive').first()).toBeVisible();
});

test('VIEWER has read-only UI and receives backend 403 for creation', async ({ page }) => {
  await login(page, viewerEmail);
  await page.getByRole('link', { name: 'Clients' }).click();
  await expect(page.getByTestId('create-client')).toHaveCount(0);
  await page.goto(`/clients/${viewerClientId}`);
  await expect(page.getByTestId('client-details-page')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit client' })).toHaveCount(0);
  await expect(page.getByTestId('details-status')).toHaveCount(0);
  await page.goto('/clients/new');
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();

  const api = await request.newContext({ baseURL: apiBase });
  const auth = await api.post('auth/login', { data: { email: viewerEmail, password } });
  const { accessToken } = await auth.json();
  const forbidden = await api.post('clients', { headers: { Authorization: `Bearer ${accessToken}` }, data: { clientName: 'Forbidden Client' } });
  expect(forbidden.status()).toBe(403);
  await api.dispose();
});

test('duplicate ICE displays a clear field conflict and creates no duplicate', async ({ page }) => {
  const first = `Duplicate First ${runId}`;
  const second = `Duplicate Second ${runId}`;
  const duplicateIce = ice(4);
  await login(page, adminEmail);
  await createThroughUi(page, first, duplicateIce);
  await expect(page.getByText('Client created successfully.')).toBeVisible();
  await page.getByText('← Back to clients').click();
  await page.getByTestId('create-client').click();
  await page.getByLabel(/Client name/).fill(second);
  await page.getByLabel('ICE').fill(duplicateIce);
  await page.getByTestId('client-submit').click();
  await expect(page.getByText('A client with this ICE already exists')).toBeVisible();
  await page.getByRole('link', { name: /Back to clients/ }).click();
  await page.getByLabel('Search clients').fill(second);
  await expect(page.getByText('No clients found')).toBeVisible();
});

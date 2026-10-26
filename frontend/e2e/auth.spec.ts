import { expect, request, test } from '@playwright/test';

const adminEmail = process.env.INITIAL_ADMIN_EMAIL ?? 'admin@example.com';
const password = process.env.INITIAL_ADMIN_PASSWORD ?? 'AdminPassword123!';
const managerEmail = 'e2e-manager@example.com';
const viewerEmail = 'e2e-viewer@example.com';

test.describe.configure({ mode: 'serial' });

test.beforeAll(async () => {
  const api = await request.newContext({ baseURL: 'http://127.0.0.1:8080/api/v1/' });
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
  await api.dispose();
});

async function login(page: import('@playwright/test').Page, email: string, suppliedPassword = password) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(suppliedPassword);
  await page.getByTestId('login-submit').click();
}

test('redirects unauthenticated users from protected pages', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

test('rejects an invalid login without revealing account existence', async ({ page }) => {
  await login(page, adminEmail, 'incorrect');
  await expect(page.getByText('Invalid email or password')).toBeVisible();
});

test('admin logs in, restores the session, opens Users, and logs out', async ({ page }) => {
  await login(page, adminEmail);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
  await page.getByRole('link', { name: 'Users' }).click();
  await expect(page.getByTestId('users-page')).toBeVisible();
  await page.getByTestId('logout').click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

for (const account of [
  { role: 'INVOICE_MANAGER', email: managerEmail },
  { role: 'VIEWER', email: viewerEmail },
]) {
  test(`${account.role} cannot open Users`, async ({ page }) => {
    await login(page, account.email);
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0);
    await page.goto('/users');
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    const result = await page.evaluate(async () => {
      const response = await fetch('http://127.0.0.1:8080/api/v1/users', { credentials: 'include' });
      return response.status;
    });
    expect(result).toBe(401);
  });
}

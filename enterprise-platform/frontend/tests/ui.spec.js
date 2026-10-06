const { test, expect } = require('@playwright/test');
const origin = 'http://127.0.0.1:8123';
test('unauthenticated pages offer OAuth login without exposing credentials', async ({ page }) => {
  await page.route('**/bff/session', route => route.fulfill({ status: 401, contentType: 'application/problem+json', body: '{"detail":"Login required"}' }));
  await page.goto(origin);
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toHaveAttribute('href', '/oauth2/authorization/task-management-ui');
  await page.goto(origin + '/kpi-ui/index.html');
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toHaveAttribute('href', '/oauth2/authorization/kpi-ui');
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});
test('employee navigation cannot expose manager pages and titles are escaped', async ({ page }) => {
  await page.route('**/bff/**', route => {
    const path = new URL(route.request().url()).pathname;
    const value = path === '/bff/session' ? { subject: 'employee', roles: ['EMPLOYEE'], csrfToken: 'test' } :
      path === '/bff/api/v1/reference/teams' ? [] :
      { content: [{ id: 'one', title: '<script>window.injection=true</script>', status: 'APPROVED', priority: 'HIGH', dueDate: null }], totalElements: 1, hasNext: false };
    return route.fulfill({ json: value });
  });
  await page.goto(origin + '/#me');
  await expect(page.getByRole('link', { name: 'All tasks', exact: true })).toHaveCount(0);
  await expect(page.getByText('<script>window.injection=true</script>')).toBeVisible();
  expect(await page.evaluate(() => window.injection)).toBeUndefined();
  await page.goto(origin + '/#create');
  await expect(page.getByRole('alert')).toContainText('Your role cannot access this page');
});
test('style guide supports keyboard focus, modal and responsive layout', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(origin + '/style-guide.html');
  await page.getByRole('button', { name: 'Open modal' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Show toast' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Changes saved.' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
test('KPI charts use API values and render an empty state', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/bff/**', route => {
    const path = new URL(route.request().url()).pathname;
    let value;
    if (path === '/bff/session') value = { subject: 'employee', roles: ['EMPLOYEE'], csrfToken: 'test' };
    else if (path.endsWith('/trends') || path.endsWith('/distribution')) value = { data: { content: [], page: 0, size: 16, hasNext: false } };
    else value = { data: { score: 0, completionPercentage: 0, onTimePercentage: 0, counts: { total: 0, completed: 0, overdue: 0, meanCompletionHours: 0 } }, dataAsOf: new Date().toISOString(), stale: false };
    return route.fulfill({ json: value });
  });
  await page.goto(origin + '/kpi-ui/index.html');
  await expect(page.getByText('No task data in this period.')).toBeVisible();
  await expect(page.locator('canvas')).toHaveCount(3);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(page.getByRole('link', { name: 'Company performance' })).toHaveCount(0);
});

test('navigation during a pending dashboard request renders the newest route', async ({ page }) => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route('**/bff/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/bff/session') return route.fulfill({ json: { subject: 'manager', roles: ['PROJECT_MANAGER'], csrfToken: 'test' } });
    if (path === '/bff/api/v1/reference/teams') return route.fulfill({ json: [] });
    await pending;
    return route.fulfill({ json: { content: [], totalElements: 0, hasNext: false } });
  });
  await page.goto(origin);
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.getByRole('link', { name: 'Create task', exact: true }).click();
  release();
  await expect(page.getByLabel('Title', { exact: true })).toBeVisible();
});

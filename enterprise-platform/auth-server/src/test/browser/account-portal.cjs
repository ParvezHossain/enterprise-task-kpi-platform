// Browser rendering checks use production assets and fixture API data; HTTP security has AccountPortalIT.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { chromium } = require('../../../../frontend/node_modules/playwright');

const resources = path.resolve(__dirname, '../../main/resources');
const assets = {
  '/account': ['account/index.html', 'text/html'],
  '/account-assets/account.css': ['static/account-assets/account.css', 'text/css'],
  '/account-assets/account.js': ['static/account-assets/account.js', 'text/javascript']
};

(async () => {
  const browser = await chromium.launch({ channel: 'chromium', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let activityFailure = false, signedOut = false, identityFailure = false;
    let email = 'developer@example.test';
    const events = Array.from({ length: 12 }, (_, index) => ({
      id: `fixture-event-${index}`,
      event: ['LOGIN_SUCCEEDED', 'AUTH_LOGOUT', 'LOGIN_FAILED'][index % 3],
      occurredAt: new Date(Date.UTC(2026, 9, 6, 12 - index)).toISOString()
    }));
    const account = () => ({ subject: '11111111-1111-4111-8111-111111111111', email, roles: ['ADMIN'],
      csrfToken: 'ui-fixture-csrf', csrfParameterName: '_csrf',
      taskUrl: 'http://127.0.0.1:8080/', kpiUrl: 'http://127.0.0.1:8081/' });
    await page.route('http://auth-portal.test/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (assets[url.pathname]) {
        const [file, contentType] = assets[url.pathname];
        return route.fulfill({ body: fs.readFileSync(path.join(resources, file), 'utf8'), contentType,
          headers: { 'Content-Security-Policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'" } });
      }
      if (url.pathname === '/api/v1/account')
        return route.fulfill({ status: identityFailure ? 401 : 200, json: identityFailure ? {} : account() });
      if (url.pathname === '/api/v1/account/activity') {
        const current = Number(url.searchParams.get('page'));
        return route.fulfill({ status: activityFailure ? 503 : 200,
          json: { content: events.slice(current * 10, (current + 1) * 10), page: current, size: 10, hasNext: current === 0 } });
      }
      if (url.pathname === '/logout') {
        assert.equal(request.method(), 'POST');
        assert.equal(new URLSearchParams(request.postData()).get('_csrf'), 'ui-fixture-csrf');
        signedOut = true;
        return route.fulfill({ status: 302, headers: { Location: '/login?logout' } });
      }
      if (url.pathname === '/login') return route.fulfill({ contentType: 'text/html', body: '<h1>Signed out of Auth</h1>' });
      return route.fulfill({ status: 404 });
    });

    await page.goto('http://auth-portal.test/account');
    await page.locator('#activity-list li').nth(9).waitFor();
    assert.equal(await page.locator('#email').textContent(), email);
    assert.equal(await page.locator('#task-link').getAttribute('href'), 'http://127.0.0.1:8080/');
    assert.equal(await page.locator('#kpi-link').getAttribute('href'), 'http://127.0.0.1:8081/');
    assert.equal(await page.locator('#logout-button').isEnabled(), true);
    assert.equal(await page.locator('#previous').isDisabled(), true);
    await page.screenshot({ path: path.join(os.tmpdir(), 'auth-account-portal.png'), fullPage: true });

    await page.locator('#next').click();
    await page.waitForFunction(() => document.getElementById('page-label').textContent === 'Page 2');
    assert.equal(await page.locator('#activity-list li').count(), 2);
    assert.equal(await page.locator('#next').isDisabled(), true);
    await page.locator('#previous').click();
    await page.waitForFunction(() => document.getElementById('page-label').textContent === 'Page 1');

    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.locator('.logout-shortcut').isVisible(), true);
    assert.equal(await page.locator('.logout-shortcut').getAttribute('href'), '#sign-out');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(os.tmpdir(), 'auth-account-portal-mobile.png'), fullPage: true });
    activityFailure = true;
    await page.locator('#refresh').click();
    await page.waitForFunction(() => document.getElementById('activity-status').textContent.includes('temporarily unavailable'));
    assert.equal(await page.locator('#logout-button').isEnabled(), true);
    activityFailure = false;

    email = '<img src=x onerror="window.portalXss=true">';
    await page.reload();
    await page.locator('#activity-list li').nth(9).waitFor();
    assert.equal(await page.locator('#email').textContent(), email);
    assert.equal(await page.locator('#email img').count(), 0);
    assert.equal(await page.evaluate(() => window.portalXss === undefined), true);
    await page.locator('#logout-button').click();
    await page.waitForURL('**/login?logout');
    assert.equal(signedOut, true);

    identityFailure = true;
    await page.goto('http://auth-portal.test/account');
    await page.locator('#account-error').waitFor({ state: 'visible' });
    assert.equal(await page.locator('#logout-button').isDisabled(), true);
    assert.equal(await page.locator('#account-error a').getAttribute('href'), '/login');
    assert.deepEqual(errors, []);
    console.log('PASS: portal desktop/mobile layout, pagination, trusted app links, safe text rendering, errors and CSRF form logout.');
    console.log(`Fixture screenshots: ${path.join(os.tmpdir(), 'auth-account-portal.png')} and ${path.join(os.tmpdir(), 'auth-account-portal-mobile.png')}`);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });

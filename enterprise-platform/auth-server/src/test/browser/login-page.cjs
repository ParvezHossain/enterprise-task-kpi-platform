// Production presentation with fixture CSRF/server responses. AccountPortalIT covers real security.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { chromium } = require('../../../../frontend/node_modules/playwright');
const resources = path.resolve(__dirname, '../../main/resources');
const csp = "default-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

(async () => {
  const browser = await chromium.launch({ channel: 'chromium', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let validLogin = false, submitted = false;
    const routePage = async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname === '/login' && request.method() === 'POST') {
        const fields = new URLSearchParams(request.postData());
        assert.equal(fields.get('_csrf'), 'fixture-csrf-token');
        assert.equal(fields.get('username'), 'developer@example.test');
        assert.equal(fields.get('password'), 'ui-fixture-password');
        assert.ok(request.headers().accept.includes('text/html'));
        submitted = true;
        return route.fulfill({ status: 302, headers: { Location: validLogin ? '/account?continue' : '/login?error' } });
      }
      if (url.pathname === '/login' || url.pathname === '/logout') {
        const file = url.pathname === '/login' ? 'account/login.html' : 'account/logout.html';
        const html = fs.readFileSync(path.join(resources, file), 'utf8')
          .replaceAll('{{csrfName}}', '_csrf').replaceAll('{{csrfToken}}', 'fixture-csrf-token')
          .replaceAll('{{errorHidden}}', url.searchParams.has('error') ? '' : 'hidden')
          .replaceAll('{{logoutHidden}}', url.searchParams.has('logout') ? '' : 'hidden');
        return route.fulfill({ contentType: 'text/html', body: html, headers: { 'Content-Security-Policy': csp } });
      }
      if (url.pathname === '/account') return route.fulfill({ contentType: 'text/html', body: '<h1>Your account</h1>' });
      if (url.pathname.startsWith('/account-assets/')) {
        const files = { '/account-assets/login.css': ['static/account-assets/login.css', 'text/css'],
          '/account-assets/login.js': ['static/account-assets/login.js', 'text/javascript'] };
        const asset = files[url.pathname];
        if (asset) return route.fulfill({ contentType: asset[1], body: fs.readFileSync(path.join(resources, asset[0]), 'utf8') });
      }
      return route.fulfill({ status: 404 });
    };
    await context.route('http://auth-login.test/**', routePage);
    await page.goto('http://auth-login.test/login');
    await page.locator('#password-toggle').waitFor({ state: 'visible' });
    assert.equal(await page.locator('#login-error').isHidden(), true);
    assert.equal(await page.locator('#logout-notice').isHidden(), true);
    assert.equal(await page.locator('#username').getAttribute('autocomplete'), 'username');
    assert.equal(await page.locator('#password').getAttribute('autocomplete'), 'current-password');
    assert.equal(await page.locator('form').getAttribute('action'), '/login');
    await page.screenshot({ path: path.join(os.tmpdir(), 'auth-login-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.equal(await page.locator('#sign-in').isVisible(), true);
    await page.screenshot({ path: path.join(os.tmpdir(), 'auth-login-mobile.png'), fullPage: true });
    await page.locator('#password-toggle').focus();
    await page.keyboard.press('Space');
    assert.equal(await page.locator('#password').getAttribute('type'), 'text');
    assert.equal(await page.locator('#password-toggle').getAttribute('aria-pressed'), 'true');
    await page.locator('#password-toggle').click();
    assert.equal(await page.locator('#password').getAttribute('type'), 'password');
    await page.locator('#sign-in').click();
    assert.equal(submitted, false); // Native required/email validation remains active.
    await page.locator('#username').fill('developer@example.test');
    await page.locator('#password').fill('ui-fixture-password');
    await page.locator('#password').press('Enter');
    await page.waitForURL('**/login?error');
    assert.equal(submitted, true);
    assert.equal(await page.locator('#login-error').isVisible(), true);
    assert.equal(await page.locator('#password').inputValue(), '');
    assert.equal(await page.locator('#sign-in').isEnabled(), true);
    assert.equal(await page.locator('body').textContent().then(text => text.includes('ui-fixture-password')), false);
    validLogin = true;
    await page.locator('#username').fill('developer@example.test');
    await page.locator('#password').fill('ui-fixture-password');
    await page.locator('#sign-in').click();
    await page.waitForURL('**/account?continue');
    await page.goto('http://auth-login.test/login?logout');
    assert.equal(await page.locator('#logout-notice').isVisible(), true);
    assert.ok((await page.locator('#logout-notice').textContent()).includes('manage their own sessions'));
    await page.goto('http://auth-login.test/logout');
    assert.equal(await page.locator('form').getAttribute('method'), 'post');
    assert.equal(await page.locator('[name="_csrf"]').getAttribute('value'), 'fixture-csrf-token');

    const plainContext = await browser.newContext({ javaScriptEnabled: false });
    await plainContext.route('http://auth-login.test/**', routePage);
    const plainPage = await plainContext.newPage();
    await plainPage.goto('http://auth-login.test/login');
    assert.equal(await plainPage.locator('#password-toggle').isHidden(), true);
    await plainPage.locator('#username').fill('developer@example.test');
    await plainPage.locator('#password').fill('ui-fixture-password');
    await plainPage.locator('#sign-in').click();
    await plainPage.waitForURL('**/account?continue');
    assert.deepEqual(errors, []);
    console.log('PASS: login desktop/mobile, keyboard password toggle, validation, native CSRF POST, errors, redirects, logout notice and JavaScript-free sign-in.');
    console.log(`Screenshots: ${path.join(os.tmpdir(), 'auth-login-desktop.png')} and ${path.join(os.tmpdir(), 'auth-login-mobile.png')}`);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });

const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  testDir: './tests',
  testMatch: process.env.E2E_REAL ? 'workflow.spec.js' : 'ui.spec.js',
  workers: 1,
  retries: 0,
  timeout: 180000,
  use: { channel: 'chromium', headless: true, actionTimeout: 15000, navigationTimeout: 30000, trace: 'off', screenshot: 'off' },
  reporter: 'list',
  webServer: process.env.E2E_REAL ? undefined : {
    command: 'python3 tests/serve.py',
    port: 8123,
    reuseExistingServer: false
  }
});

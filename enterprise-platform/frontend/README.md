# Frontends
Plain HTML5/JavaScript with shared Tailwind/Chart.js assets and Nginx hosting.
No framework, OAuth secret or OAuth token is stored in the browser.

Prerequisites: Node 24 LTS/npm, Python 3; Docker/Java 25 for real workflow tests.

From the repository root:

    npm --prefix enterprise-platform/frontend ci
    npm --prefix enterprise-platform/frontend run build
    cd enterprise-platform/frontend
    npx playwright install chromium
    npm test

UI tests use a local static server and mock HTTP contracts. Return to the repository
root and start the healthy seeded Compose stack for real workflow tests:

    npm --prefix enterprise-platform/frontend run test:e2e -- --repeat-each=3

Each repeat creates new users/team/project/task. Private fixtures remain under
.local; browser traces/screenshots are disabled to avoid saving auth material.

Task: http://127.0.0.1:8080. KPI: http://127.0.0.1:8081.
Both expose /style-guide.html. Assets are served locally, without CDN dependencies.
See [development](../../docs/development.md#complete-local-platform).

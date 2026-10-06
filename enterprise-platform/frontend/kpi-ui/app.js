import { api, loadSession, session, has, escape as e, errorView, logout } from '/shared/api.js';
import '/shared/chart.js';
const main = document.querySelector('#content');
let charts = [], teamId = '', from = '', to = '', rankPage = 0;
const login = '<section class="card max-w-lg mx-auto mt-12"><span class="badge">Performance workspace</span><h1 class="mt-4">See the progress behind the numbers.</h1><p class="muted my-6">Explore completion, timeliness and performance across your work.</p><a class="btn" href="/oauth2/authorization/kpi-ui">Sign in</a></section>';
function allowed(route) {
  if (route === 'company') return has('ADMIN', 'PROJECT_MANAGER');
  if (['team', 'ranking', 'top-performers'].includes(route)) return has('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEADER');
  return true;
}
const manager = () => has('ADMIN', 'PROJECT_MANAGER');
function params() {
  const value = new URLSearchParams(); if (teamId) value.set('teamId', teamId); if (from) value.set('from', from); if (to) value.set('to', to); return value;
}
let routing = false, routePending = false;
async function route() {
  if (routing) { routePending = true; return; }
  routing = true;
  try { await renderRoute(); }
  finally {
    routing = false;
    if (routePending) { routePending = false; await route(); }
  }
}
async function renderRoute() {
  const route = location.hash.slice(1) || 'dashboard';
  if (!allowed(route)) { main.innerHTML = errorView(new Error('Your role cannot access this page.')); return; }
  for (const chart of charts) chart.destroy(); charts = [];
  const scope = route === 'company' ? 'company' : route === 'team' ? 'team' : 'me';
  main.innerHTML = '<h1>' + e(({ dashboard: 'Performance at a glance', me: 'My performance', team: 'Team performance', company: 'Company performance', ranking: 'Rankings', 'top-performers': 'Top performers', trends: 'Trend analysis', profile: 'Profile' })[route] || 'Performance') + '</h1>' +
    '<p class="muted mt-2 mb-6">Scores reflect completed work, timeliness, priority and overdue tasks.</p>' +
    '<form id="filters" class="card grid sm:grid-cols-4 gap-4 mb-6">' +
    (has('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEADER') ? '<div><label for="team">Team ID</label><input id="team" value="' + e(teamId) + '" placeholder="Optional team UUID"></div>' : '') +
    '<div><label for="from">From</label><input type="date" id="from" value="' + e(from) + '"></div><div><label for="to">To</label><input type="date" id="to" value="' + e(to) + '"></div><button class="btn self-end">Apply dates</button></form><div id="data" aria-live="polite"><p role="status">Loading metrics…</p></div>';
  document.querySelector('#filters').onsubmit = event => { event.preventDefault(); teamId = document.querySelector('#team')?.value || ''; from = document.querySelector('#from').value; to = document.querySelector('#to').value; rankPage = 0; routeView().catch(showError); };
  if (route === 'profile') { document.querySelector('#data').innerHTML = '<section class="card"><p class="break-all">' + e(session.subject) + '</p><p class="muted mt-3">' + e(session.roles.join(', ')) + '</p></section>'; return; }
  function showError(error) { document.querySelector('#data').innerHTML = errorView(error); }
  async function routeView() {
    const target = document.querySelector('#data'), query = params();
    if ((scope === 'team' || (['ranking', 'top-performers'].includes(route) && !manager())) && !teamId) { target.innerHTML = '<section class="card empty">Enter a team ID to view its performance.</section>'; return; }
    if (['ranking', 'top-performers'].includes(route)) {
      query.set('page', rankPage); query.set('size', route === 'top-performers' ? 10 : 20);
      const result = await api('/bff/api/v1/kpis/' + route + '?' + query), page = result.data;
      target.innerHTML = '<p class="muted mb-4">' + freshness(result) + '</p><section class="card overflow-x-auto"><table><thead><tr><th>Employee</th><th>Score</th><th>Completed</th><th>Overdue</th></tr></thead><tbody>' + page.content.map(row => '<tr><td>' + e(row.employeeId) + '</td><td class="font-semibold">' + row.score + '</td><td>' + row.counts.completed + '</td><td>' + row.counts.overdue + '</td></tr>').join('') + '</tbody></table>' +
        (!page.content.length ? '<div class="empty">No ranked employees in this period.</div>' : '') + '<div class="flex gap-3 mt-5"><button id="prev" class="btn btn-secondary"' + (!rankPage ? ' disabled' : '') + '>Previous</button><button id="next" class="btn btn-secondary"' + (!page.hasNext ? ' disabled' : '') + '>Next</button></div><div class="h-72 mt-6"><canvas id="ranking-chart" aria-label="Employee ranking"></canvas></div></section>';
      document.querySelector('#prev').onclick = () => { rankPage--; routeView().catch(showError); }; document.querySelector('#next').onclick = () => { rankPage++; routeView().catch(showError); };
      chart('ranking-chart', 'bar', page.content.map(row => row.employeeId.slice(0, 8)), [{ label: 'Score', data: page.content.map(row => row.score), backgroundColor: '#0f766e' }]); return;
    }
    const result = await api('/bff/api/v1/kpis/' + scope + '?' + query), value = result.data;
    query.set('scope', scope);
    const [trend, distribution] = await Promise.all([api('/bff/api/v1/kpis/trends?' + query), api('/bff/api/v1/kpis/distribution?' + query)]);
    target.innerHTML = '<p class="muted mb-4">' + freshness(result) + '</p><div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">' +
      [['Score', value.score], ['Completion', value.completionPercentage.toFixed(1) + '%'], ['On time', value.onTimePercentage.toFixed(1) + '%'], ['Completed', value.counts.completed]].map(([label, value]) => '<section class="card"><p class="muted">' + label + '</p><p class="text-4xl font-semibold mt-3">' + value + '</p></section>').join('') +
      '</div><div class="grid lg:grid-cols-2 gap-5 mt-5"><section class="card"><h2>Completion trend</h2><div class="h-72 mt-5"><canvas id="trend" aria-label="Monthly completions"></canvas></div></section><section class="card"><h2>Completed vs overdue</h2><div class="h-72 mt-5"><canvas id="completion" aria-label="Completed and overdue task counts"></canvas></div></section><section class="card"><h2>Status distribution</h2><div class="h-72 mt-5"><canvas id="distribution" aria-label="Task status distribution"></canvas></div></section><section class="card"><h2>Completion time</h2><p class="text-4xl font-semibold mt-5">' + value.counts.meanCompletionHours.toFixed(1) + '<span class="muted ml-2">hours</span></p><p class="muted mt-4">Measured from start to completion. Tasks with missing timestamps are excluded.</p>' + (!value.counts.total ? '<p class="empty">No task data in this period.</p>' : '') + '</section></div>';
    chart('trend', 'line', trend.data.content.map(row => row.month), [{ label: 'Completed', data: trend.data.content.map(row => row.completed), borderColor: '#0f766e' }]);
    chart('completion', 'bar', ['Completed', 'Overdue'], [{ label: 'Tasks', data: [value.counts.completed, value.counts.overdue], backgroundColor: ['#0f766e', '#e11d48'] }]);
    chart('distribution', 'doughnut', distribution.data.content.map(row => row.status), [{ data: distribution.data.content.map(row => row.count), backgroundColor: ['#94a3b8', '#0d9488', '#38bdf8', '#6366f1', '#f59e0b'] }]);
  }
  try { await routeView(); } catch (error) { showError(error); }
}
function freshness(result) { return (result.stale ? 'Data is stale. Last complete snapshot: ' : 'Data as of ') + e(new Date(result.dataAsOf).toLocaleString()); }
function chart(id, type, labels, datasets) {
  for (const old of charts.filter(chart => chart.canvas.id === id)) old.destroy();
  charts = charts.filter(chart => chart.canvas.id !== id);
  charts.push(new window.Chart(document.getElementById(id), { type, data: { labels, datasets }, options: { responsive: true, maintainAspectRatio: false, animation: false, ...(type !== 'doughnut' ? { scales: { y: { beginAtZero: true } } } : {}) } }));
}
try {
  await loadSession();
  document.querySelector('#navigation').innerHTML = [['dashboard', 'Dashboard'], ['me', 'My performance'], ['team', 'Team performance'], ['company', 'Company performance'], ['ranking', 'Rankings'], ['top-performers', 'Top performers'], ['trends', 'Trends'], ['profile', 'Profile']].filter(([route]) => allowed(route)).map(([route, label]) => '<a class="nav-link" href="#' + route + '">' + label + '</a>').join('');
  document.querySelector('#logout').hidden = false; document.querySelector('#logout').onclick = () => logout().catch(error => main.innerHTML = errorView(error));
  window.addEventListener('hashchange', () => { rankPage = 0; route(); });
  await route();
} catch (error) { main.innerHTML = error.status === 401 ? login : errorView(error); }

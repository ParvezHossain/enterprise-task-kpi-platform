import { api, loadSession, session, has, escape as e, errorView, toast, logout } from '/shared/api.js';
const main = document.querySelector('#content');
let teams = [], page = 0, selectedTeam = '', status = '', priority = '', query = '';
const login = '<section class="card max-w-lg mx-auto mt-12"><span class="badge">Task workspace</span><h1 class="mt-4">A clear path from draft to done.</h1><p class="muted my-6">Sign in to create, approve, assign and complete work with your team.</p><a class="btn" href="/oauth2/authorization/task-management-ui">Sign in</a></section>';
function allowed(route) {
  if (['all', 'create'].includes(route)) return has('ADMIN', 'PROJECT_MANAGER');
  if (route === 'team') return has('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEADER');
  return true;
}
function navigation() {
  const items = [['dashboard', 'Dashboard'], ['me', 'My tasks'], ['team', 'Team tasks'], ['all', 'All tasks'], ['create', 'Create task'], ['profile', 'Profile']];
  document.querySelector('#navigation').innerHTML = items.filter(([route]) => allowed(route)).map(([route, label]) => '<a class="nav-link" href="#' + route + '">' + label + '</a>').join('');
  document.querySelector('#logout').hidden = false;
  document.querySelector('#logout').onclick = () => logout().catch(error => toast(error.message));
}
const teamOptions = () => teams.map(team => '<option value="' + e(team.id) + '"' + (team.id === selectedTeam ? ' selected' : '') + '>' + e(team.name) + '</option>').join('');
const dates = value => value ? new Date(value).toLocaleDateString() : '—';
async function dashboard() {
  const scope = has('ADMIN', 'PROJECT_MANAGER') ? '' : '/me';
  const results = await Promise.all(['', 'COMPLETED', 'IN_PROGRESS', 'CLOSED'].map(value => api('/bff/api/v1/tasks' + scope + '?size=1' + (value ? '&status=' + value : ''))));
  main.innerHTML = '<h1>Your work, in view.</h1><p class="muted mt-2 mb-8">Keep decisions moving and make progress visible.</p><div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">' +
    ['Tasks', 'Completed', 'In progress', 'Closed'].map((label, index) => '<div class="card"><p class="muted">' + label + '</p><p class="text-4xl font-semibold mt-3">' + results[index].totalElements + '</p></div>').join('') +
    '</div><section class="card mt-6"><h2>Next steps</h2><p class="muted my-4">Find your assigned tasks or review work awaiting a team decision.</p><div class="flex gap-3"><a class="btn" href="#me">Open my tasks</a>' +
    (has('ADMIN', 'PROJECT_MANAGER') ? '<a class="btn btn-secondary" href="#create">Create task</a>' : '') + '</div></section>';
}
async function list(scope) {
  const suffix = scope === 'all' ? '' : '/' + scope;
  main.innerHTML = '<div class="flex justify-between items-center mb-6"><h1>' + ({ me: 'My tasks', team: 'Team tasks', all: 'All tasks' })[scope] + '</h1>' +
    (has('ADMIN', 'PROJECT_MANAGER') ? '<a class="btn" href="#create">Create task</a>' : '') + '</div>' +
    '<form id="filters" class="card grid sm:grid-cols-2 lg:grid-cols-5 gap-3 mb-5">' +
    (scope === 'team' ? '<div><label for="team">Team</label><select id="team" required><option value="">Choose a team</option>' + teamOptions() + '</select></div>' : '') +
    '<div><label for="query">Search</label><input id="query" value="' + e(query) + '" placeholder="Task title"></div><div><label for="status">Status</label><select id="status"><option value="">Any status</option>' +
    ['DRAFT', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'].map(value => '<option' + (status === value ? ' selected' : '') + '>' + value + '</option>').join('') +
    '</select></div><div><label for="priority">Priority</label><select id="priority"><option value="">Any priority</option>' +
    ['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map(value => '<option' + (priority === value ? ' selected' : '') + '>' + value + '</option>').join('') +
    '</select></div><button class="btn self-end">Apply filters</button></form><section id="results" class="card overflow-x-auto"><p role="status">Loading tasks…</p></section>';
  document.querySelector('#filters').onsubmit = event => {
    event.preventDefault(); page = 0;
    selectedTeam = document.querySelector('#team')?.value || selectedTeam;
    status = document.querySelector('#status').value; priority = document.querySelector('#priority').value; query = document.querySelector('#query').value;
    results().catch(error => document.querySelector('#results').innerHTML = errorView(error));
  };
  async function results() {
    const target = document.querySelector('#results');
    if (scope === 'team' && !selectedTeam) { target.innerHTML = '<div class="empty">Choose a team to view its tasks.</div>'; return; }
    const params = new URLSearchParams({ page, size: 20 });
    if (scope === 'team') params.set('teamId', selectedTeam);
    if (status) params.set('status', status);
    if (priority) params.set('priority', priority);
    if (query) params.set('title', query);
    const response = await api('/bff/api/v1/tasks' + suffix + '?' + params);
    target.innerHTML = response.content.length ? '<table><thead><tr><th>Task</th><th>Status</th><th>Priority</th><th>Due</th></tr></thead><tbody>' +
      response.content.map(task => '<tr><td><a class="text-teal-700 font-medium" href="#details/' + e(task.id) + '">' + e(task.title) + '</a></td><td><span class="badge">' + e(task.status) + '</span></td><td>' + e(task.priority) + '</td><td>' + e(dates(task.dueDate)) + '</td></tr>').join('') +
      '</tbody></table>' : '<div class="empty">No tasks match these filters.</div>';
    target.innerHTML += '<div class="flex justify-between items-center mt-5"><button id="previous" class="btn btn-secondary"' + (page === 0 ? ' disabled' : '') + '>Previous</button><span class="muted">Page ' + (page + 1) + ' · ' + response.totalElements + ' tasks</span><button id="next" class="btn btn-secondary"' + (!response.hasNext ? ' disabled' : '') + '>Next</button></div>';
    document.querySelector('#previous').onclick = () => { page--; results().catch(error => target.innerHTML = errorView(error)); };
    document.querySelector('#next').onclick = () => { page++; results().catch(error => target.innerHTML = errorView(error)); };
  }
  await results();
}
async function create() {
  main.innerHTML = '<h1>Create a task</h1><p class="muted mt-2 mb-6">New work starts as a draft for a Team Leader to approve.</p><form id="create" class="card max-w-3xl space-y-4"><div id="feedback"></div><div><label for="title">Title</label><input id="title" name="title" maxlength="200" required></div><div><label for="description">Description</label><textarea id="description" name="description" maxlength="10000" rows="4"></textarea></div><div class="grid sm:grid-cols-2 gap-4"><div><label for="team">Team</label><select id="team" required><option value="">Choose a team</option>' + teamOptions() +
    '</select></div><div><label for="project">Project</label><select id="project" required><option value="">Choose a team first</option></select></div><div><label for="priority">Priority</label><select id="priority"><option>MEDIUM</option><option>LOW</option><option>HIGH</option><option>URGENT</option></select></div><div><label for="due">Due date</label><input type="date" id="due" min="' + new Date().toISOString().slice(0, 10) + '"></div></div><button class="btn" type="submit">Create draft</button></form>';
  document.querySelector('#team').onchange = async event => {
    try {
      const projects = await api('/bff/api/v1/reference/projects?teamId=' + event.target.value);
      document.querySelector('#project').innerHTML = projects.map(project => '<option value="' + e(project.id) + '">' + e(project.name) + '</option>').join('');
    } catch (error) { document.querySelector('#feedback').innerHTML = errorView(error); }
  };
  document.querySelector('#create').onsubmit = async event => {
    event.preventDefault(); const button = event.submitter; button.disabled = true;
    try {
      const task = await api('/bff/api/v1/tasks', { method: 'POST', body: { title: document.querySelector('#title').value, description: document.querySelector('#description').value, teamId: document.querySelector('#team').value, projectId: document.querySelector('#project').value, priority: document.querySelector('#priority').value, dueDate: document.querySelector('#due').value || null } });
      toast('Draft created'); location.hash = '#details/' + task.id;
    } catch (error) { document.querySelector('#feedback').innerHTML = errorView(error); button.disabled = false; }
  };
}
async function details(id) {
  const task = await api('/bff/api/v1/tasks/' + encodeURIComponent(id));
  main.innerHTML = '<a href="#me" class="text-teal-700 text-sm">← Back to tasks</a><div class="flex flex-wrap justify-between items-center gap-4 mt-4 mb-6"><h1>' + e(task.title) + '</h1><span class="badge">' + e(task.status) + '</span></div><div id="feedback"></div><div class="grid lg:grid-cols-3 gap-5"><section class="card lg:col-span-2"><h2>Details</h2><p class="whitespace-pre-wrap my-5">' + e(task.description || 'No description provided.') + '</p><dl class="grid grid-cols-2 gap-3 text-sm"><dt class="muted">Priority</dt><dd>' + e(task.priority) + '</dd><dt class="muted">Due date</dt><dd>' + e(dates(task.dueDate)) + '</dd><dt class="muted">Assigned to</dt><dd class="break-all">' + e(task.assignedTo || 'Unassigned') + '</dd><dt class="muted">Version</dt><dd>' + task.version + '</dd></dl></section><section class="card"><h2>Actions</h2><div id="actions" class="space-y-3 mt-4"></div></section></div><section class="card mt-5"><h2>History</h2><div id="history" class="mt-4 overflow-x-auto"></div></section>';
  const actions = document.querySelector('#actions');
  const canManage = has('ADMIN', 'TEAM_LEADER');
  if (canManage && task.status === 'DRAFT') actions.innerHTML += '<button class="btn w-full" data-action="approve">Approve</button>';
  if (canManage && task.status === 'APPROVED' && !task.assignedTo) {
    const members = await api('/bff/api/v1/reference/members?teamId=' + task.teamId);
    actions.innerHTML += '<label for="assignee">Assign to team member</label><select id="assignee">' + members.map(member => '<option>' + e(member.userId) + '</option>').join('') + '</select><button class="btn w-full" data-action="assign"' + (!members.length ? ' disabled' : '') + '>Assign</button>';
  }
  if (has('EMPLOYEE') && task.assignedTo === session.subject && task.status === 'APPROVED') actions.innerHTML += '<button class="btn w-full" data-action="start">Start task</button>';
  if (has('EMPLOYEE') && task.assignedTo === session.subject && task.status === 'IN_PROGRESS') actions.innerHTML += '<button class="btn w-full" data-action="complete">Complete task</button>';
  if (has('ADMIN', 'PROJECT_MANAGER') && task.status === 'COMPLETED') actions.innerHTML += '<button class="btn w-full" data-action="close">Close task</button>';
  if (!actions.innerHTML) actions.innerHTML = '<p class="muted">No actions are available for your role at this stage.</p>';
  for (const button of actions.querySelectorAll('[data-action]')) {
    const key = crypto.randomUUID();
    button.onclick = async () => {
      button.disabled = true;
      const action = button.dataset.action, statusAction = action === 'start' || action === 'complete';
      const body = { version: task.version };
      if (statusAction) body.status = action === 'start' ? 'IN_PROGRESS' : 'COMPLETED';
      if (action === 'assign') body.employeeId = document.querySelector('#assignee').value;
      try {
        await api('/bff/api/v1/tasks/' + task.id + '/' + (statusAction ? 'status' : action), { method: statusAction ? 'PATCH' : 'POST', body, key: statusAction ? undefined : key });
        toast('Task updated'); await details(id);
      } catch (error) { document.querySelector('#feedback').innerHTML = errorView(error); button.disabled = false; }
    };
  }
  let historyPage = 0;
  async function history() {
    const result = await api('/bff/api/v1/tasks/' + task.id + '/history?page=' + historyPage + '&size=20');
    document.querySelector('#history').innerHTML = result.content.length ? '<table><thead><tr><th>Action</th><th>Transition</th><th>Actor</th><th>When</th></tr></thead><tbody>' +
      result.content.map(event => '<tr><td>' + e(event.action) + '</td><td>' + e(event.previousStatus || '—') + ' → ' + e(event.newStatus) + '</td><td class="break-all">' + e(event.actorId) + '</td><td>' + e(new Date(event.occurredAt).toLocaleString()) + '</td></tr>').join('') +
      '</tbody></table><div class="flex gap-3 mt-4"><button id="history-prev" class="btn btn-secondary"' + (historyPage === 0 ? ' disabled' : '') + '>Previous events</button><button id="history-next" class="btn btn-secondary"' + (!result.hasNext ? ' disabled' : '') + '>Next events</button></div>' : '<p class="empty">No history yet.</p>';
    if (result.content.length) {
      document.querySelector('#history-prev').onclick = () => { historyPage--; history().catch(error => toast(error.message)); };
      document.querySelector('#history-next').onclick = () => { historyPage++; history().catch(error => toast(error.message)); };
    }
  }
  await history();
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
  const [route = 'dashboard', id] = (location.hash.slice(1) || 'dashboard').split('/');
  if (!allowed(route)) { main.innerHTML = errorView(new Error('Your role cannot access this page.')); return; }
  main.innerHTML = '<p role="status" class="muted">Loading…</p>';
  for (const link of document.querySelectorAll('.nav-link')) link.classList.toggle('nav-active', link.hash === '#' + route);
  try {
    if (route === 'dashboard') await dashboard();
    else if (['me', 'team', 'all'].includes(route)) await list(route);
    else if (route === 'create') await create();
    else if (route === 'details' && id) await details(id);
    else if (route === 'profile') main.innerHTML = '<h1>Profile</h1><section class="card mt-6"><p class="muted">User ID</p><p class="break-all my-3">' + e(session.subject) + '</p><p>' + session.roles.map(role => '<span class="badge mr-2">' + e(role) + '</span>').join('') + '</p></section>';
    else main.innerHTML = errorView(new Error('Page not found.'));
  } catch (error) { main.innerHTML = error.status === 401 ? login : errorView(error); }
}
try {
  await loadSession();
  teams = await api('/bff/api/v1/reference/teams');
  navigation();
  window.addEventListener('hashchange', () => { page = 0; route(); });
  await route();
} catch (error) { main.innerHTML = error.status === 401 ? login : errorView(error); }

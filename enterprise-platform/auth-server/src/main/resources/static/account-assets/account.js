'use strict';

(() => {
  const element = id => document.getElementById(id);
  let page = 0;
  let pending = false;
  let hasNext = false;

  async function get(path) {
    const response = await fetch(path, { credentials: 'same-origin', headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (response.status === 401) throw new Error('Your Auth session has ended. Open the sign-in page to continue.');
    if (!response.ok) throw new Error('This information is temporarily unavailable. Please retry.');
    return response.json();
  }

  function pagination() {
    element('previous').disabled = pending || page === 0;
    element('next').disabled = pending || !hasNext;
    element('refresh').disabled = pending;
    element('page-label').textContent = `Page ${page + 1}`;
  }

  async function history(requestedPage = page) {
    if (pending) return;
    pending = true;
    pagination();
    element('activity-status').textContent = 'Loading activity…';
    try {
      const result = await get(`/api/v1/account/activity?page=${requestedPage}&size=10`);
      page = result.page;
      hasNext = result.hasNext;
      element('activity-list').replaceChildren();
      const labels = { LOGIN_SUCCEEDED: 'Signed in to Auth', LOGIN_FAILED: 'Sign-in unsuccessful', AUTH_LOGOUT: 'Signed out of Auth' };
      for (const entry of result.content) {
        const item = document.createElement('li');
        const mark = document.createElement('span');
        mark.className = `event-mark ${entry.event === 'LOGIN_FAILED' ? 'failed' : ''}`;
        mark.textContent = entry.event === 'LOGIN_FAILED' ? '!' : entry.event === 'AUTH_LOGOUT' ? '↪' : '✓';
        mark.setAttribute('aria-hidden', 'true');
        const text = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = labels[entry.event] || 'Authentication event';
        const time = document.createElement('time');
        time.dateTime = entry.occurredAt;
        time.textContent = new Date(entry.occurredAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
        text.append(title, time);
        item.append(mark, text);
        element('activity-list').append(item);
      }
      element('activity-status').textContent = result.content.length ? '' : 'No recorded events on this page.';
    } catch (error) {
      element('activity-status').textContent = error.message;
    } finally {
      pending = false;
      pagination();
    }
  }

  async function start() {
    try {
      const account = await get('/api/v1/account');
      element('email').textContent = account.email;
      element('subject').textContent = account.subject;
      for (const role of account.roles) {
        const badge = document.createElement('span');
        badge.className = 'role';
        badge.textContent = role.replaceAll('_', ' ');
        element('roles').append(badge);
      }
      element('task-link').href = account.taskUrl;
      element('kpi-link').href = account.kpiUrl;
      element('task-link').hidden = false;
      element('kpi-link').hidden = false;
      element('logout-csrf').name = account.csrfParameterName;
      element('logout-csrf').value = account.csrfToken;
      element('logout-button').disabled = false;
      element('previous').addEventListener('click', () => history(page - 1));
      element('next').addEventListener('click', () => history(page + 1));
      element('refresh').addEventListener('click', () => history());
      await history();
    } catch (error) {
      const alert = element('account-error');
      alert.textContent = `${error.message} `;
      const login = document.createElement('a');
      login.href = '/login';
      login.textContent = 'Open sign-in';
      alert.append(login);
      alert.hidden = false;
      element('email').textContent = 'Account unavailable';
      element('activity-status').textContent = 'Sign in to view your activity.';
    }
  }

  start();
})();

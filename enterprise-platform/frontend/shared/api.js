export let session;

export async function loadSession() {
    session = await api('/bff/session');
    return session;
}

export async function api(path, {method = 'GET', body, key} = {}) {
    const id = crypto.randomUUID();
    const headers = {'X-Request-ID': id};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && session) headers['X-CSRF-TOKEN'] = session.csrfToken;
    if (key) headers['Idempotency-Key'] = key;
    let response;
    try {
        response = await fetch(path, {
            method,
            credentials: 'same-origin',
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new Error('Unable to reach the service. Check your connection and retry.');
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(data?.detail || 'The request could not be processed.');
        error.status = response.status;
        error.fields = data?.errors;
        error.requestId = response.headers.get('X-Request-ID') || id;
        throw error;
    }
    return data;
}

export function has(...roles) {
    return roles.some(role => session?.roles.includes(role));
}

export function escape(value = '') {
    return String(value ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[c]);
}

export function errorView(error) {
    return '<div role="alert" class="error">' + escape(error.message) + (error.fields ? '<ul>' + Object.entries(error.fields).map(([name, message]) => '<li>' + escape(name) + ': ' + escape(message) + '</li>').join('') + '</ul>' : '') + (error.requestId ? '<p class="text-xs mt-2">Request ' + escape(error.requestId) + '</p>' : '') + '</div>';
}

export function toast(message) {
    const box = document.querySelector('#toast');
    box.textContent = message;
    box.hidden = false;
    setTimeout(() => {
        box.hidden = true;
    }, 4000);
}

export async function logout() {
    const response = await fetch('/bff/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'X-CSRF-TOKEN': session.csrfToken}
    });
    if (!response.ok) throw new Error('Sign out failed. Please retry.');
    location.assign('/');
}

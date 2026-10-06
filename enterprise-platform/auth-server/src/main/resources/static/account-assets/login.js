'use strict';

(() => {
  const form = document.getElementById('login-form');
  const password = document.getElementById('password');
  const toggle = document.getElementById('password-toggle');
  const submit = document.getElementById('sign-in');
  const label = document.getElementById('sign-in-label');
  const capsLock = document.getElementById('caps-lock');

  toggle.hidden = false;
  toggle.addEventListener('click', () => {
    const visible = password.type === 'password';
    password.type = visible ? 'text' : 'password';
    toggle.textContent = visible ? 'Hide' : 'Show';
    toggle.setAttribute('aria-pressed', String(visible));
  });
  const caps = event => { capsLock.hidden = !event.getModifierState('CapsLock'); };
  password.addEventListener('keydown', caps);
  password.addEventListener('keyup', caps);
  password.addEventListener('blur', () => { capsLock.hidden = true; });
  form.addEventListener('submit', () => {
    // Submit natively: OAuth saved-request redirects must navigate the browser.
    submit.disabled = true;
    label.textContent = 'Signing in…';
  });
  window.addEventListener('pageshow', () => {
    submit.disabled = false;
    label.textContent = 'Sign in';
    password.type = 'password';
    toggle.textContent = 'Show';
    toggle.setAttribute('aria-pressed', 'false');
  });
})();

document.querySelector('#open').onclick = () => document.querySelector('#modal').showModal();
document.querySelector('#notify').onclick = () => { const toast = document.querySelector('#toast'); toast.hidden = false; setTimeout(() => toast.hidden = true, 3000); };
document.querySelector('form').onsubmit = event => event.preventDefault();

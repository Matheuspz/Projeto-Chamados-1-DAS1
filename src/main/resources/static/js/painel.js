'use strict';

const sidebar = document.getElementById('sidebar');
const mobileMenu = document.getElementById('mobileMenu');

if (sidebar && mobileMenu) {
    mobileMenu.addEventListener('click', () => sidebar.classList.toggle('open'));
    document.addEventListener('click', (event) => {
        if (window.innerWidth <= 760 && sidebar.classList.contains('open')
            && !sidebar.contains(event.target) && !mobileMenu.contains(event.target)) {
            sidebar.classList.remove('open');
        }
    });
}

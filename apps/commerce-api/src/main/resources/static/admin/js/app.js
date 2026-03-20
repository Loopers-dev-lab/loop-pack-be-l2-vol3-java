import { initDashboard } from './components/dashboard.js';
import { initBrands } from './components/brands.js';
import { initProducts } from './components/products.js';
import { initOrders } from './components/orders.js';
import { initCoupons } from './components/coupons.js';
import { initDataGenerator } from './components/data-generator.js';
import { initUsers } from './components/users.js';

// === Tab Router ===
const tabs = { dashboard: initDashboard, brands: initBrands, products: initProducts,
    orders: initOrders, coupons: initCoupons, users: initUsers, 'data-generator': initDataGenerator };

function switchTab(tabName) {
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));

    const panel = document.getElementById(`tab-${tabName}`);
    const link = document.querySelector(`[data-tab="${tabName}"]`);
    if (panel) panel.classList.add('active');
    if (link) link.classList.add('active');

    if (tabs[tabName]) tabs[tabName]();
}

// === Modal ===
window.Modal = {
    open(title, bodyHtml) {
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-body').innerHTML = bodyHtml;
        document.getElementById('modal-overlay').classList.remove('hidden');
    },
    close() {
        document.getElementById('modal-overlay').classList.add('hidden');
    }
};

// === Toast ===
window.Toast = {
    show(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => toast.remove(), 3000);
    },
    success(msg) { this.show(msg, 'success'); },
    error(msg) { this.show(msg, 'error'); },
    info(msg) { this.show(msg, 'info'); },
};

// === Pagination Helper ===
window.Pagination = {
    render(containerId, currentPage, totalPages, onChange) {
        const container = document.getElementById(containerId);
        if (!container || totalPages <= 1) { if (container) container.innerHTML = ''; return; }

        let html = `<button ${currentPage === 0 ? 'disabled' : ''} onclick="this._prev()">&#8592; Prev</button>`;
        html += `<span class="page-info">${currentPage + 1} / ${totalPages}</span>`;
        html += `<button ${currentPage >= totalPages - 1 ? 'disabled' : ''} onclick="this._next()">Next &#8594;</button>`;
        container.innerHTML = html;

        container.querySelector('button:first-child')._prev = () => onChange(currentPage - 1);
        container.querySelector('button:last-child')._next = () => onChange(currentPage + 1);

        container.querySelectorAll('button').forEach(btn => {
            const fn = btn._prev || btn._next;
            if (fn) btn.addEventListener('click', fn);
        });
    }
};

// === Init ===
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        switchTab(link.dataset.tab);
    });
});

document.getElementById('modal-overlay').addEventListener('click', (e) => {
    if (e.target === e.currentTarget) Modal.close();
});

switchTab('dashboard');

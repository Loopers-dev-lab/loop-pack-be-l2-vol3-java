import { Auth } from './auth.js';
import { UserApi } from './api.js';
import { initHome } from './pages/home.js';
import { initProduct } from './pages/product.js';
import { initLogin } from './pages/login.js';
import { initMyPage } from './pages/mypage.js';
import { initCoupons } from './pages/coupons.js';
import { initBlackFriday } from './pages/blackfriday.js';
import { initRanking } from './pages/ranking.js';

// === Globals ===
window.Modal = {
    open(title, html) {
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-body').innerHTML = html;
        document.getElementById('modal-overlay').classList.add('open');
    },
    close() {
        document.getElementById('modal-overlay').classList.remove('open');
    },
};
document.getElementById('modal-close').addEventListener('click', Modal.close);
document.getElementById('modal-overlay').addEventListener('click', (e) => {
    if (e.target === e.currentTarget) Modal.close();
});

window.Toast = {
    show(msg, type = 'success') {
        const c = document.getElementById('toast-container');
        const el = document.createElement('div');
        el.className = `toast toast-${type}`;
        el.textContent = msg;
        c.appendChild(el);
        setTimeout(() => el.remove(), 3000);
    },
    success(msg) { this.show(msg, 'success'); },
    error(msg) { this.show(msg, 'error'); },
};

// === Header Auth UI ===
async function updateHeaderActions() {
    const el = document.getElementById('header-actions');
    if (Auth.isLoggedIn()) {
        let pointText = '';
        try {
            const data = await UserApi.getPoint();
            pointText = `<span class="header-point">${data.point.toLocaleString()}P</span>`;
        } catch { /* ignore */ }
        el.innerHTML = `
            ${pointText}
            <span class="user-name">${esc(Auth.getName() || Auth.get().loginId)}님</span>
            <button class="btn btn-sm btn-outline" id="logout-btn">로그아웃</button>`;
        document.getElementById('logout-btn').addEventListener('click', () => {
            Auth.clear();
            updateHeaderActions();
            navigate('home');
        });
    } else {
        el.innerHTML = `<a href="#login" class="btn btn-sm btn-primary">로그인</a>`;
    }
}

// === Router ===
const routes = {
    home: initHome,
    product: initProduct,
    login: initLogin,
    coupons: initCoupons,
    mypage: initMyPage,
    blackfriday: initBlackFriday,
    ranking: initRanking,
};

function navigate(page, params = {}) {
    window._routeParams = params;
    location.hash = page;
}
window.navigate = navigate;

async function router() {
    const hash = location.hash.slice(1) || 'home';
    const [page, ...rest] = hash.split('/');
    const params = { id: rest[0], ...window._routeParams };
    window._routeParams = {};

    // Auth guard
    if ((page === 'mypage' || page === 'blackfriday') && !Auth.isLoggedIn()) {
        Toast.error('로그인이 필요합니다');
        location.hash = 'login';
        return;
    }

    // Nav active
    document.querySelectorAll('.nav-link').forEach(el => {
        el.classList.toggle('active', el.dataset.page === page);
    });

    updateHeaderActions();

    const init = routes[page] || routes.home;
    await init(params);
}

window.addEventListener('hashchange', router);
router();

function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

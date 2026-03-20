import { ProductApi, BrandApi } from '../api.js';

let currentPage = 0;
let currentBrandId = null;
let currentSort = null;
let brands = [];

const SORT_OPTIONS = [
    { value: '', label: '기본' },
    { value: 'price_asc', label: '가격 낮은순' },
    { value: 'price_desc', label: '가격 높은순' },
    { value: 'likes_desc', label: '좋아요순' },
];

export async function initHome() {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="shop-layout">
            <aside class="brand-sidebar" id="brand-sidebar">
                <h3>Brands</h3>
                <input type="text" class="brand-search" id="brand-search" placeholder="검색">
                <ul class="brand-list" id="brand-list"></ul>
            </aside>
            <div class="shop-content">
                <div class="filter-bar" id="filter-bar">
                    <div class="sort-chips" id="sort-chips">
                        ${SORT_OPTIONS.map(o => `
                            <button class="sort-chip${o.value === (currentSort || '') ? ' active' : ''}"
                                    data-sort="${o.value}">${o.label}</button>
                        `).join('')}
                    </div>
                    <span class="result-count" id="result-count"></span>
                </div>
                <div class="product-grid" id="product-grid"></div>
                <div class="pagination" id="pagination"></div>
            </div>
        </div>`;

    document.getElementById('sort-chips').addEventListener('click', (e) => {
        const chip = e.target.closest('.sort-chip');
        if (!chip) return;
        document.querySelectorAll('.sort-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        currentSort = chip.dataset.sort || null;
        loadProducts(0);
    });

    document.getElementById('brand-search').addEventListener('input', (e) => {
        filterBrandList(e.target.value.trim().toLowerCase());
    });

    await loadBrands();
    await loadProducts(currentPage);
}

function getChosung(str) {
    const cho = ['ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ',
                 'ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'];
    const code = str.charCodeAt(0) - 0xAC00;
    if (code < 0 || code > 11171) return str.charAt(0).toUpperCase();
    return cho[Math.floor(code / 588)];
}

function sortBrands(brandList) {
    return [...brandList].sort((a, b) => {
        const aKor = a.name.charCodeAt(0) >= 0xAC00 && a.name.charCodeAt(0) <= 0xD7A3;
        const bKor = b.name.charCodeAt(0) >= 0xAC00 && b.name.charCodeAt(0) <= 0xD7A3;
        if (aKor && !bKor) return -1;
        if (!aKor && bKor) return 1;
        return a.name.localeCompare(b.name, aKor ? 'ko' : 'en');
    });
}

async function loadBrands() {
    try {
        const data = await BrandApi.list(0, 200);
        brands = sortBrands(data.items);
        renderBrandList(brands);
    } catch { /* ignore */ }
}

function renderBrandList(list) {
    const ul = document.getElementById('brand-list');
    let html = `<li class="brand-item${currentBrandId === null ? ' active' : ''}" data-id="">전체</li>`;
    list.forEach(b => {
        html += `<li class="brand-item${currentBrandId == b.id ? ' active' : ''}" data-id="${b.id}">${esc(b.name)}</li>`;
    });
    ul.innerHTML = html;

    ul.addEventListener('click', (e) => {
        const item = e.target.closest('.brand-item');
        if (!item) return;
        ul.querySelectorAll('.brand-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
        currentBrandId = item.dataset.id || null;
        loadProducts(0);
    });
}

function filterBrandList(query) {
    const ul = document.getElementById('brand-list');
    const items = ul.querySelectorAll('.brand-item');
    items.forEach(item => {
        if (!item.dataset.id) { item.style.display = ''; return; }
        const name = item.textContent.toLowerCase();
        item.style.display = name.includes(query) ? '' : 'none';
    });
}

async function loadProducts(page) {
    currentPage = page;
    try {
        const data = await ProductApi.list(page, 20, currentBrandId, currentSort);
        const grid = document.getElementById('product-grid');

        document.getElementById('result-count').textContent =
            `총 ${data.totalElements.toLocaleString()}개 상품`;

        if (data.items.length === 0) {
            grid.innerHTML = '<div class="empty-state"><div class="icon">📦</div><p>상품이 없습니다</p></div>';
        } else {
            grid.innerHTML = data.items.map(p => `
                <a href="#product/${p.id}" class="product-card">
                    <div class="product-card-img">${p.thumbnailUrl
                        ? `<img src="${esc(p.thumbnailUrl)}" alt="${esc(p.name)}">`
                        : getInitial(p.name)}</div>
                    <div class="product-card-body">
                        <div class="product-card-brand">${esc(p.brandName)}</div>
                        <div class="product-card-name">${esc(p.name)}</div>
                        <div class="product-card-footer">
                            <span class="product-card-price">${p.price.toLocaleString()}원</span>
                            <span class="product-card-likes">&hearts; ${formatCount(p.likeCount)}</span>
                        </div>
                    </div>
                </a>`).join('');
        }

        renderPagination(data);
    } catch (e) { Toast.error(e.message); }
}

function renderPagination(data) {
    const c = document.getElementById('pagination');
    if (data.totalPages <= 1) { c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-sm btn-secondary" id="pg-prev" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages}</span>
        <button class="btn btn-sm btn-secondary" id="pg-next" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    document.getElementById('pg-prev').addEventListener('click', () => loadProducts(data.page - 1));
    document.getElementById('pg-next').addEventListener('click', () => loadProducts(data.page + 1));
}

function getInitial(name) {
    return (name || '?').charAt(0);
}

function formatCount(n) {
    if (n >= 10000) return (n / 10000).toFixed(1) + '만';
    if (n >= 1000) return (n / 1000).toFixed(1) + '천';
    return n;
}

function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

import { RankingApi } from '../api.js';

let currentPage = 1;
let currentDate = null;

export async function initRanking() {
    const app = document.getElementById('app');
    const today = new Date().toISOString().slice(0, 10);
    app.innerHTML = `
        <div class="ranking-container">
            <div class="ranking-header">
                <div class="ranking-title-area">
                    <h1 class="ranking-title">POPULAR RANKING</h1>
                    <p class="ranking-subtitle">오늘의 인기상품</p>
                </div>
                <div class="ranking-date-picker">
                    <input type="date" id="ranking-date" class="ranking-date-input" value="${today}" max="${today}">
                </div>
            </div>
            <div class="ranking-list" id="ranking-list"></div>
            <div class="pagination" id="ranking-pagination"></div>
        </div>`;

    document.getElementById('ranking-date').addEventListener('change', (e) => {
        currentDate = e.target.value || null;
        loadRanking(1);
    });

    currentDate = null;
    currentPage = 1;
    await loadRanking(currentPage);
}

function formatDate(dateStr) {
    return dateStr.replace(/-/g, '');
}

async function loadRanking(page) {
    currentPage = page;
    try {
        const apiDate = currentDate ? formatDate(currentDate) : null;
        const data = await RankingApi.list(apiDate, page, 20);
        const list = document.getElementById('ranking-list');

        if (!data.items || data.items.length === 0) {
            list.innerHTML = '<div class="empty-state"><div class="icon">--</div><p>아직 랭킹 데이터가 없습니다</p></div>';
            document.getElementById('ranking-pagination').innerHTML = '';
            return;
        }

        list.innerHTML = data.items.map(item => `
            <a href="#product/${item.productId}" class="ranking-item">
                <div class="ranking-rank ${item.rank <= 3 ? 'ranking-rank-top' : ''} ${item.rank === 1 ? 'ranking-rank-1' : ''}">
                    ${item.rank}
                </div>
                <div class="ranking-thumb">
                    ${item.thumbnailUrl
                        ? `<img src="${esc(item.thumbnailUrl)}" alt="${esc(item.productName)}">`
                        : `<span>${(item.productName || '?').charAt(0)}</span>`}
                </div>
                <div class="ranking-info">
                    <div class="ranking-brand">${esc(item.brandName)}</div>
                    <div class="ranking-name">${esc(item.productName)}</div>
                </div>
                <div class="ranking-right">
                    <div class="ranking-price">${item.price.toLocaleString()}원</div>
                    <div class="ranking-score">score ${item.score.toFixed(1)}</div>
                </div>
            </a>`).join('');

        renderPagination(data);
    } catch (e) { Toast.error(e.message); }
}

function renderPagination(data) {
    const c = document.getElementById('ranking-pagination');
    const totalPages = Math.ceil(data.totalElements / data.size);
    if (totalPages <= 1) { c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-sm btn-secondary" id="rk-prev" ${data.page <= 1 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page} / ${totalPages}</span>
        <button class="btn btn-sm btn-secondary" id="rk-next" ${data.page >= totalPages ? 'disabled' : ''}>다음</button>`;
    document.getElementById('rk-prev').addEventListener('click', () => loadRanking(data.page - 1));
    document.getElementById('rk-next').addEventListener('click', () => loadRanking(data.page + 1));
}

function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

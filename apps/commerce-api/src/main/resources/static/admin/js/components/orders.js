import { OrderApi } from '../api.js';

let currentPage = 0;

export async function initOrders() {
    const panel = document.getElementById('tab-orders');
    panel.innerHTML = `
        <div class="panel">
            <div class="panel-header"><h2>주문 관리</h2></div>
            <div class="table-wrap"><table>
                <thead><tr><th>주문 ID</th><th>결제 금액</th><th>할인 금액</th><th>상태</th><th>주문일</th><th>액션</th></tr></thead>
                <tbody id="order-tbody"></tbody>
            </table></div>
            <div class="pagination" id="order-pagination"></div>
        </div>`;
    await loadOrders(0);
}

async function loadOrders(page) {
    currentPage = page;
    try {
        const data = await OrderApi.list(page, 20);
        const tbody = document.getElementById('order-tbody');
        tbody.innerHTML = data.items.length === 0
            ? '<tr><td colspan="6" style="text-align:center;color:#94a3b8">주문이 없습니다</td></tr>'
            : data.items.map(o => `<tr>
                <td>${o.orderId}</td>
                <td>${o.totalPrice.toLocaleString()}원</td>
                <td>${o.discountAmount.toLocaleString()}원</td>
                <td><span class="badge ${statusBadge(o.status)}">${o.status}</span></td>
                <td>${formatDate(o.createdAt)}</td>
                <td><button class="btn btn-secondary btn-sm" onclick="window._orderDetail(${o.orderId})">상세</button></td>
            </tr>`).join('');

        renderPagination('order-pagination', data, loadOrders);
    } catch (e) { Toast.error(e.message); }
}

window._orderDetail = async (id) => {
    try {
        const o = await OrderApi.get(id);
        const itemsHtml = (o.items || []).map(i => `
            <tr><td>${i.orderItemId}</td><td>${esc(i.productName)}</td><td>${esc(i.brandName)}</td>
            <td>${i.orderPrice.toLocaleString()}원</td><td>${i.quantity}</td></tr>`).join('');

        Modal.open(`주문 #${o.orderId} 상세`, `
            <div style="margin-bottom:12px">
                <strong>상태:</strong> <span class="badge ${statusBadge(o.status)}">${o.status}</span>
                &nbsp; <strong>결제 금액:</strong> ${o.totalPrice.toLocaleString()}원
            </div>
            <table><thead><tr><th>항목 ID</th><th>상품명</th><th>브랜드</th><th>가격</th><th>수량</th></tr></thead>
            <tbody>${itemsHtml}</tbody></table>`);
    } catch (e) { Toast.error(e.message); }
};

function statusBadge(s) {
    if (s === 'COMPLETED' || s === 'CONFIRMED') return 'badge-green';
    if (s === 'CANCELLED') return 'badge-red';
    return 'badge-gray';
}
function renderPagination(containerId, data, loadFn) {
    const c = document.getElementById(containerId);
    if (!c || data.totalPages <= 1) { if (c) c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-secondary btn-sm" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages}</span>
        <button class="btn btn-secondary btn-sm" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    const btns = c.querySelectorAll('button');
    btns[0].addEventListener('click', () => loadFn(data.page - 1));
    btns[1].addEventListener('click', () => loadFn(data.page + 1));
}
function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/'/g, "\\'").replace(/"/g, '&quot;'); }

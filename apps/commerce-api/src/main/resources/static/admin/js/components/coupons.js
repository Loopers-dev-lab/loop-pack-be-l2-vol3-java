import { CouponApi } from '../api.js';

let currentPage = 0;

export async function initCoupons() {
    const panel = document.getElementById('tab-coupons');
    panel.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h2>쿠폰 관리</h2>
                <button class="btn btn-primary" id="coupon-add-btn">+ 쿠폰 등록</button>
            </div>
            <div class="table-wrap"><table>
                <thead><tr><th>ID</th><th>이름</th><th>할인 유형</th><th>할인 값</th><th>발급/총수량</th><th>만료일</th><th>액션</th></tr></thead>
                <tbody id="coupon-tbody"></tbody>
            </table></div>
            <div class="pagination" id="coupon-pagination"></div>
        </div>`;

    document.getElementById('coupon-add-btn').addEventListener('click', showCreateModal);
    await loadCoupons(0);
}

async function loadCoupons(page) {
    currentPage = page;
    try {
        const data = await CouponApi.list(page, 20);
        const tbody = document.getElementById('coupon-tbody');
        tbody.innerHTML = data.items.length === 0
            ? '<tr><td colspan="7" style="text-align:center;color:#94a3b8">쿠폰이 없습니다</td></tr>'
            : data.items.map(c => `<tr>
                <td>${c.id}</td>
                <td><strong>${esc(c.name)}</strong></td>
                <td><span class="badge badge-blue">${c.discountType}</span></td>
                <td>${c.discountValue.toLocaleString()}</td>
                <td>${c.issuedQuantity} / ${c.totalQuantity}</td>
                <td>${formatDate(c.expiredAt)}</td>
                <td>
                    <button class="btn btn-secondary btn-sm" onclick="window._couponIssues(${c.id},'${esc(c.name)}')">발급 내역</button>
                    <button class="btn btn-danger btn-sm" onclick="window._couponDel(${c.id},'${esc(c.name)}')">삭제</button>
                </td>
            </tr>`).join('');

        renderPagination('coupon-pagination', data, loadCoupons);
    } catch (e) { Toast.error(e.message); }
}

function showCreateModal() {
    Modal.open('쿠폰 등록', `
        <div class="form-group"><label>쿠폰명</label><input id="cpn-name"></div>
        <div class="form-row">
            <div class="form-group"><label>할인 유형</label>
                <select id="cpn-type"><option value="FIXED">정액 할인</option><option value="RATE">정률 할인</option></select>
            </div>
            <div class="form-group"><label>할인 값</label><input id="cpn-value" type="number" min="1" value="1000"></div>
        </div>
        <div class="form-row">
            <div class="form-group"><label>최소 주문 금액</label><input id="cpn-min" type="number" min="0" value="0"></div>
            <div class="form-group"><label>총 발급 수량</label><input id="cpn-qty" type="number" min="1" value="100"></div>
        </div>
        <div class="form-group"><label>만료일시</label><input id="cpn-exp" type="datetime-local"></div>
        <button class="btn btn-primary" id="cpn-save-btn" style="width:100%">저장</button>`);

    const exp = document.getElementById('cpn-exp');
    const d = new Date(); d.setMonth(d.getMonth() + 1);
    exp.value = d.toISOString().slice(0, 16);

    document.getElementById('cpn-save-btn').addEventListener('click', async () => {
        try {
            await CouponApi.create({
                name: document.getElementById('cpn-name').value.trim(),
                discountType: document.getElementById('cpn-type').value,
                discountValue: +document.getElementById('cpn-value').value,
                minOrderAmount: +document.getElementById('cpn-min').value,
                totalQuantity: +document.getElementById('cpn-qty').value,
                expiredAt: new Date(document.getElementById('cpn-exp').value).toISOString(),
            });
            Modal.close(); Toast.success('쿠폰이 등록되었습니다'); await loadCoupons(currentPage);
        } catch (e) { Toast.error(e.message); }
    });
}

window._couponIssues = async (id, name) => {
    try {
        const data = await CouponApi.issues(id, 0, 50);
        const rows = (data.items || []).map(i => `
            <tr><td>${i.ownedCouponId}</td><td>${i.userId}</td><td><span class="badge ${i.status === 'USED' ? 'badge-green' : 'badge-gray'}">${i.status}</span></td>
            <td>${formatDate(i.issuedAt)}</td></tr>`).join('');

        Modal.open(`${name} - 발급 내역`, rows
            ? `<table><thead><tr><th>ID</th><th>유저</th><th>상태</th><th>발급일</th></tr></thead><tbody>${rows}</tbody></table>`
            : '<p style="color:#94a3b8;text-align:center">발급 내역이 없습니다</p>');
    } catch (e) { Toast.error(e.message); }
};

window._couponDel = async (id, name) => {
    if (!confirm(`"${name}" 쿠폰을 삭제하시겠습니까?`)) return;
    try { await CouponApi.delete(id); Toast.success('쿠폰이 삭제되었습니다'); await loadCoupons(currentPage); }
    catch (e) { Toast.error(e.message); }
};

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

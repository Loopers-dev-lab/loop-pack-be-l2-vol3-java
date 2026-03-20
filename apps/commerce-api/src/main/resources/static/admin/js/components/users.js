import { UserApi } from '../api.js';

let currentPage = 0;

export async function initUsers() {
    const panel = document.getElementById('tab-users');
    panel.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h2>유저 관리</h2>
                <button class="btn btn-primary" id="user-point-all-btn">전체 포인트 지급</button>
            </div>
            <div class="table-wrap"><table>
                <thead><tr><th>ID</th><th>아이디</th><th>이름</th><th>이메일</th><th>포인트</th><th>가입일</th><th>액션</th></tr></thead>
                <tbody id="user-tbody"></tbody>
            </table></div>
            <div class="pagination" id="user-pagination"></div>
        </div>`;

    document.getElementById('user-point-all-btn').addEventListener('click', showAddPointAllModal);
    await loadUsers(0);
}

async function loadUsers(page) {
    currentPage = page;
    try {
        const data = await UserApi.list(page, 20);
        const tbody = document.getElementById('user-tbody');
        tbody.innerHTML = data.items.length === 0
            ? '<tr><td colspan="7" style="text-align:center;color:#94a3b8">유저가 없습니다</td></tr>'
            : data.items.map(u => `<tr>
                <td>${u.id}</td>
                <td><strong>${esc(u.loginId)}</strong></td>
                <td>${esc(u.name)}</td>
                <td>${esc(u.email)}</td>
                <td><strong>${u.point.toLocaleString()}P</strong></td>
                <td>${formatDate(u.createdAt)}</td>
                <td>
                    <button class="btn btn-primary btn-sm" onclick="window._userAddPoint(${u.id},'${esc(u.loginId)}',${u.point})">포인트 지급</button>
                </td>
            </tr>`).join('');

        renderPagination('user-pagination', data, loadUsers);
    } catch (e) { Toast.error(e.message); }
}

function showAddPointAllModal() {
    Modal.open('전체 유저 포인트 지급', `
        <div class="form-group"><label>지급 포인트</label><input id="all-point-amount" type="number" min="1" value="100000"></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
            <button class="btn btn-secondary btn-sm quick-all" data-amount="10000">10,000P</button>
            <button class="btn btn-secondary btn-sm quick-all" data-amount="100000">100,000P</button>
            <button class="btn btn-secondary btn-sm quick-all" data-amount="1000000">1,000,000P</button>
            <button class="btn btn-secondary btn-sm quick-all" data-amount="10000000">10,000,000P</button>
        </div>
        <button class="btn btn-primary" id="all-point-save-btn" style="width:100%">전체 지급</button>`);

    document.querySelectorAll('.quick-all').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('all-point-amount').value = btn.dataset.amount;
        });
    });

    document.getElementById('all-point-save-btn').addEventListener('click', async () => {
        const amount = +document.getElementById('all-point-amount').value;
        if (amount <= 0) { Toast.error('금액을 입력해주세요'); return; }
        try {
            const result = await UserApi.addPointAll(amount);
            Modal.close();
            Toast.success(result.message);
            await loadUsers(currentPage);
        } catch (e) { Toast.error(e.message); }
    });
}

window._userAddPoint = (id, loginId, currentPoint) => {
    Modal.open(`${loginId} 포인트 지급`, `
        <div style="margin-bottom:16px;text-align:center">
            <div style="font-size:13px;color:#64748b">현재 보유 포인트</div>
            <div style="font-size:24px;font-weight:800;color:#6366f1">${currentPoint.toLocaleString()}P</div>
        </div>
        <div class="form-group"><label>지급 포인트</label><input id="user-point-amount" type="number" min="1" value="100000"></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
            <button class="btn btn-secondary btn-sm quick-one" data-amount="10000">10,000P</button>
            <button class="btn btn-secondary btn-sm quick-one" data-amount="100000">100,000P</button>
            <button class="btn btn-secondary btn-sm quick-one" data-amount="1000000">1,000,000P</button>
        </div>
        <button class="btn btn-primary" id="user-point-save-btn" style="width:100%">지급</button>`);

    document.querySelectorAll('.quick-one').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('user-point-amount').value = btn.dataset.amount;
        });
    });

    document.getElementById('user-point-save-btn').addEventListener('click', async () => {
        const amount = +document.getElementById('user-point-amount').value;
        if (amount <= 0) { Toast.error('금액을 입력해주세요'); return; }
        try {
            const result = await UserApi.addPoint(id, amount);
            Modal.close();
            Toast.success(result.message);
            await loadUsers(currentPage);
        } catch (e) { Toast.error(e.message); }
    });
};

function renderPagination(containerId, data, loadFn) {
    const c = document.getElementById(containerId);
    if (!c || data.totalPages <= 1) { if (c) c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-secondary btn-sm" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages} (총 ${data.totalElements}명)</span>
        <button class="btn btn-secondary btn-sm" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    const btns = c.querySelectorAll('button');
    btns[0].addEventListener('click', () => loadFn(data.page - 1));
    btns[1].addEventListener('click', () => loadFn(data.page + 1));
}
function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/'/g, "\\'").replace(/"/g, '&quot;'); }

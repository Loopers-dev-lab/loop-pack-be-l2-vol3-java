import { BrandApi } from '../api.js';

let currentPage = 0;

export async function initBrands() {
    const panel = document.getElementById('tab-brands');
    panel.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h2>브랜드 관리</h2>
                <button class="btn btn-primary" id="brand-add-btn">+ 브랜드 등록</button>
            </div>
            <div class="table-wrap"><table>
                <thead><tr><th>ID</th><th>이름</th><th>생성일</th><th>액션</th></tr></thead>
                <tbody id="brand-tbody"></tbody>
            </table></div>
            <div class="pagination" id="brand-pagination"></div>
        </div>`;

    document.getElementById('brand-add-btn').addEventListener('click', showCreateModal);
    await loadBrands(0);
}

async function loadBrands(page) {
    currentPage = page;
    try {
        const data = await BrandApi.list(page, 20);
        const tbody = document.getElementById('brand-tbody');
        tbody.innerHTML = data.items.length === 0
            ? '<tr><td colspan="4" style="text-align:center;color:#94a3b8">브랜드가 없습니다</td></tr>'
            : data.items.map(b => `<tr>
                <td>${b.id}</td>
                <td><strong>${b.name}</strong></td>
                <td>${formatDate(b.createdAt)}</td>
                <td>
                    <button class="btn btn-secondary btn-sm" onclick="window._brandEdit(${b.id},'${esc(b.name)}')">수정</button>
                    <button class="btn btn-danger btn-sm" onclick="window._brandDel(${b.id},'${esc(b.name)}')">삭제</button>
                </td>
            </tr>`).join('');

        renderPagination('brand-pagination', data, loadBrands);
    } catch (e) { Toast.error(e.message); }
}

function showCreateModal() {
    Modal.open('브랜드 등록', `
        <div class="form-group"><label>브랜드명</label><input id="brand-name-input" maxlength="99" placeholder="브랜드명을 입력하세요"></div>
        <button class="btn btn-primary" id="brand-save-btn" style="width:100%">저장</button>`);
    document.getElementById('brand-save-btn').addEventListener('click', async () => {
        const name = document.getElementById('brand-name-input').value.trim();
        if (!name) { Toast.error('브랜드명을 입력해주세요'); return; }
        try { await BrandApi.create(name); Modal.close(); Toast.success('브랜드가 등록되었습니다'); await loadBrands(currentPage); }
        catch (e) { Toast.error(e.message); }
    });
}

window._brandEdit = (id, name) => {
    Modal.open('브랜드 수정', `
        <div class="form-group"><label>브랜드명</label><input id="brand-name-input" maxlength="99" value="${esc(name)}"></div>
        <button class="btn btn-primary" id="brand-save-btn" style="width:100%">수정</button>`);
    document.getElementById('brand-save-btn').addEventListener('click', async () => {
        const newName = document.getElementById('brand-name-input').value.trim();
        if (!newName) { Toast.error('브랜드명을 입력해주세요'); return; }
        try { await BrandApi.update(id, newName); Modal.close(); Toast.success('브랜드가 수정되었습니다'); await loadBrands(currentPage); }
        catch (e) { Toast.error(e.message); }
    });
};

window._brandDel = async (id, name) => {
    if (!confirm(`"${name}" 브랜드를 삭제하시겠습니까?`)) return;
    try { await BrandApi.delete(id); Toast.success('브랜드가 삭제되었습니다'); await loadBrands(currentPage); }
    catch (e) { Toast.error(e.message); }
};

function renderPagination(containerId, data, loadFn) {
    const c = document.getElementById(containerId);
    if (!c || data.totalPages <= 1) { if (c) c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-secondary btn-sm" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages} (총 ${data.totalElements}건)</span>
        <button class="btn btn-secondary btn-sm" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    const btns = c.querySelectorAll('button');
    btns[0].addEventListener('click', () => loadFn(data.page - 1));
    btns[1].addEventListener('click', () => loadFn(data.page + 1));
}

function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/'/g, "\\'").replace(/"/g, '&quot;'); }

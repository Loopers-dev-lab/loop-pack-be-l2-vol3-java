import { ProductApi, BrandApi } from '../api.js';

let currentPage = 0;
let filterBrandId = null;

export async function initProducts() {
    const panel = document.getElementById('tab-products');
    panel.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h2>상품 관리</h2>
                <button class="btn btn-primary" id="product-add-btn">+ 상품 등록</button>
            </div>
            <div class="toolbar" style="margin-bottom:14px">
                <select id="product-brand-filter"><option value="">전체 브랜드</option></select>
                <button class="btn btn-secondary btn-sm" id="product-filter-btn">필터 적용</button>
            </div>
            <div class="table-wrap"><table>
                <thead><tr><th>ID</th><th>브랜드</th><th>상품명</th><th>가격</th><th>재고</th><th>생성일</th><th>액션</th></tr></thead>
                <tbody id="product-tbody"></tbody>
            </table></div>
            <div class="pagination" id="product-pagination"></div>
        </div>`;

    document.getElementById('product-add-btn').addEventListener('click', showCreateModal);
    document.getElementById('product-filter-btn').addEventListener('click', () => {
        filterBrandId = document.getElementById('product-brand-filter').value || null;
        loadProducts(0);
    });

    await loadBrandOptions();
    await loadProducts(0);
}

async function loadBrandOptions() {
    try {
        const data = await BrandApi.list(0, 200);
        const sel = document.getElementById('product-brand-filter');
        data.items.forEach(b => {
            sel.insertAdjacentHTML('beforeend', `<option value="${b.id}">${b.name}</option>`);
        });
    } catch (e) { /* ignore */ }
}

async function loadProducts(page) {
    currentPage = page;
    try {
        const data = await ProductApi.list(page, 20, filterBrandId);
        const tbody = document.getElementById('product-tbody');
        tbody.innerHTML = data.items.length === 0
            ? '<tr><td colspan="7" style="text-align:center;color:#94a3b8">상품이 없습니다</td></tr>'
            : data.items.map(p => `<tr>
                <td>${p.id}</td>
                <td><span class="badge badge-blue">${esc(p.brandName)}</span></td>
                <td><strong>${esc(p.name)}</strong></td>
                <td>${p.price.toLocaleString()}원</td>
                <td>${p.stock.toLocaleString()}</td>
                <td>${formatDate(p.createdAt)}</td>
                <td>
                    <button class="btn btn-secondary btn-sm" onclick="window._prodEdit(${p.id})">수정</button>
                    <button class="btn btn-danger btn-sm" onclick="window._prodDel(${p.id},'${esc(p.name)}')">삭제</button>
                </td>
            </tr>`).join('');

        renderPagination('product-pagination', data, loadProducts);
    } catch (e) { Toast.error(e.message); }
}

async function showCreateModal() {
    let brands = [];
    try { const d = await BrandApi.list(0, 200); brands = d.items; } catch (e) { /* */ }

    const brandOpts = brands.map(b => `<option value="${b.id}">${b.name}</option>`).join('');
    Modal.open('상품 등록', `
        <div class="form-group"><label>브랜드</label><select id="prod-brand">${brandOpts}</select></div>
        <div class="form-group"><label>상품명</label><input id="prod-name" maxlength="99"></div>
        <div class="form-row">
            <div class="form-group"><label>가격</label><input id="prod-price" type="number" min="0" value="10000"></div>
            <div class="form-group"><label>재고</label><input id="prod-stock" type="number" min="0" value="100"></div>
        </div>
        <button class="btn btn-primary" id="prod-save-btn" style="width:100%">저장</button>`);

    document.getElementById('prod-save-btn').addEventListener('click', async () => {
        try {
            await ProductApi.create({
                brandId: +document.getElementById('prod-brand').value,
                name: document.getElementById('prod-name').value.trim(),
                price: +document.getElementById('prod-price').value,
                stock: +document.getElementById('prod-stock').value,
            });
            Modal.close(); Toast.success('상품이 등록되었습니다'); await loadProducts(currentPage);
        } catch (e) { Toast.error(e.message); }
    });
}

window._prodEdit = async (id) => {
    try {
        const p = await ProductApi.get(id);
        Modal.open('상품 수정', `
            <div class="form-group"><label>상품명</label><input id="prod-name" maxlength="99" value="${esc(p.name)}"></div>
            <div class="form-row">
                <div class="form-group"><label>가격</label><input id="prod-price" type="number" min="0" value="${p.price}"></div>
                <div class="form-group"><label>재고</label><input id="prod-stock" type="number" min="0" value="${p.stock}"></div>
            </div>
            <button class="btn btn-primary" id="prod-save-btn" style="width:100%">수정</button>`);
        document.getElementById('prod-save-btn').addEventListener('click', async () => {
            try {
                await ProductApi.update(id, {
                    name: document.getElementById('prod-name').value.trim(),
                    price: +document.getElementById('prod-price').value,
                    stock: +document.getElementById('prod-stock').value,
                });
                Modal.close(); Toast.success('상품이 수정되었습니다'); await loadProducts(currentPage);
            } catch (e) { Toast.error(e.message); }
        });
    } catch (e) { Toast.error(e.message); }
};

window._prodDel = async (id, name) => {
    if (!confirm(`"${name}" 상품을 삭제하시겠습니까?`)) return;
    try { await ProductApi.delete(id); Toast.success('상품이 삭제되었습니다'); await loadProducts(currentPage); }
    catch (e) { Toast.error(e.message); }
};

function renderPagination(containerId, data, loadFn) {
    const c = document.getElementById(containerId);
    if (!c || data.totalPages <= 1) { if (c) c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-secondary btn-sm" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages} (총 ${data.totalElements.toLocaleString()}건)</span>
        <button class="btn btn-secondary btn-sm" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    const btns = c.querySelectorAll('button');
    btns[0].addEventListener('click', () => loadFn(data.page - 1));
    btns[1].addEventListener('click', () => loadFn(data.page + 1));
}

function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/'/g, "\\'").replace(/"/g, '&quot;'); }

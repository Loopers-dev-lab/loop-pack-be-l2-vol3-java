import { BrandApi, ProductApi, DataGenApi, UserApi } from '../api.js';

let isRunning = false;
let shouldStop = false;
let cachedBrands = [];
let cachedProducts = [];

export async function initDataGenerator() {
    const panel = document.getElementById('tab-data-generator');
    panel.innerHTML = `
        <h1 style="font-size:22px;font-weight:700;margin-bottom:20px">Data Generator</h1>
        <div class="stats-grid" id="gen-stats"></div>

        <!-- User Generation -->
        <div class="gen-section">
            <h3>1. User 생성</h3>
            <p class="mode-desc">테스트용 유저를 대량 생성합니다. (비밀번호: Test1234!)</p>
            <div class="gen-config">
                <div><label>접두사</label><input id="gen-user-prefix" value="testuser"></div>
                <div><label>생성 수</label><input id="gen-user-count" type="number" min="1" value="100"></div>
                <div><label>기본 포인트</label><input id="gen-user-point" type="number" min="0" value="10000000"></div>
                <div style="display:flex;align-items:end"><button class="btn btn-primary" id="gen-user-btn">유저 생성</button></div>
            </div>
            <div class="progress-bar" id="gen-user-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-user-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Brand Generation -->
        <div class="gen-section">
            <h3>2. Brand 생성</h3>
            <div class="gen-config">
                <div><label>생성 개수</label><input id="gen-brand-count" type="number" min="1" value="100"></div>
                <div><label>이름 접두사</label><input id="gen-brand-prefix" value="Brand"></div>
                <div style="display:flex;align-items:end"><button class="btn btn-primary" id="gen-brand-btn">브랜드 생성</button></div>
            </div>
            <div class="progress-bar" id="gen-brand-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-brand-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Product Generation -->
        <div class="gen-section">
            <h3>3. Product 생성</h3>
            <div class="mode-tabs" id="product-mode-tabs">
                <button class="mode-tab active" data-mode="all">전체 브랜드에 분산</button>
                <button class="mode-tab" data-mode="single">특정 브랜드에 집중</button>
                <button class="mode-tab" data-mode="multi">브랜드별 지정 수량</button>
            </div>

            <!-- Mode: 전체 분산 -->
            <div id="product-mode-all" class="mode-panel active">
                <p class="mode-desc">모든 브랜드에 균등 분산하여 상품을 생성합니다.</p>
                <div class="gen-config">
                    <div><label>총 상품 수</label><input id="gen-prod-all-count" type="number" min="1" value="100000"></div>
                    <div><label>가격 범위 (최소)</label><input id="gen-prod-all-pricemin" type="number" min="0" value="1000"></div>
                    <div><label>가격 범위 (최대)</label><input id="gen-prod-all-pricemax" type="number" min="0" value="500000"></div>
                </div>
            </div>

            <!-- Mode: 특정 브랜드 -->
            <div id="product-mode-single" class="mode-panel">
                <p class="mode-desc">선택한 하나의 브랜드에 상품을 집중 생성합니다.</p>
                <div class="gen-config">
                    <div><label>브랜드 선택</label><select id="gen-prod-single-brand"></select></div>
                    <div><label>상품 수</label><input id="gen-prod-single-count" type="number" min="1" value="1000"></div>
                    <div><label>가격 범위 (최소~최대)</label>
                        <div style="display:flex;gap:6px">
                            <input id="gen-prod-single-pricemin" type="number" min="0" value="1000" style="width:50%">
                            <input id="gen-prod-single-pricemax" type="number" min="0" value="500000" style="width:50%">
                        </div>
                    </div>
                </div>
            </div>

            <!-- Mode: 브랜드별 지정 -->
            <div id="product-mode-multi" class="mode-panel">
                <p class="mode-desc">선택한 브랜드들에 각각 지정 수량의 상품을 생성합니다.</p>
                <div class="gen-config" style="grid-template-columns:1fr 1fr">
                    <div><label>브랜드당 상품 수</label><input id="gen-prod-multi-count" type="number" min="1" value="1000"></div>
                    <div><label>가격 범위 (최소~최대)</label>
                        <div style="display:flex;gap:6px">
                            <input id="gen-prod-multi-pricemin" type="number" min="0" value="1000" style="width:50%">
                            <input id="gen-prod-multi-pricemax" type="number" min="0" value="500000" style="width:50%">
                        </div>
                    </div>
                </div>
                <div class="brand-checklist" id="gen-prod-multi-brands"></div>
            </div>

            <div style="margin-top:12px;display:flex;gap:8px;align-items:center">
                <div><label style="font-size:12px;color:#64748b">재고 범위</label>
                    <div style="display:flex;gap:6px">
                        <input id="gen-prod-stockmin" type="number" min="0" value="0" style="width:80px;padding:6px;border:1px solid #d1d5db;border-radius:4px;font-size:13px">
                        <span style="line-height:32px">~</span>
                        <input id="gen-prod-stockmax" type="number" min="0" value="1000" style="width:80px;padding:6px;border:1px solid #d1d5db;border-radius:4px;font-size:13px">
                    </div>
                </div>
                <div style="flex:1"></div>
                <button class="btn btn-primary" id="gen-product-btn" style="align-self:end">상품 생성</button>
            </div>
            <div class="progress-bar" id="gen-product-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-product-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Like Generation -->
        <div class="gen-section">
            <h3>4. Like 생성</h3>
            <div class="mode-tabs" id="likes-mode-tabs">
                <button class="mode-tab active" data-mode="all">전체 상품에 분산</button>
                <button class="mode-tab" data-mode="single">특정 상품에 집중</button>
                <button class="mode-tab" data-mode="range">상품 ID 범위 지정</button>
            </div>

            <!-- Mode: 전체 분산 -->
            <div id="likes-mode-all" class="mode-panel active">
                <p class="mode-desc">모든 상품에 좋아요를 분산 생성합니다.</p>
                <div class="gen-config">
                    <div><label>상품당 좋아요 수</label><input id="gen-likes-all-per" type="number" min="1" value="5"></div>
                    <div><label>배치 사이즈 (한 요청당 상품 수)</label><input id="gen-likes-all-batch" type="number" min="1" value="500"></div>
                </div>
            </div>

            <!-- Mode: 특정 상품 -->
            <div id="likes-mode-single" class="mode-panel">
                <p class="mode-desc">특정 상품 하나에 좋아요를 집중 생성합니다.</p>
                <div class="gen-config">
                    <div><label>상품 ID</label><input id="gen-likes-single-id" type="number" min="1" placeholder="예: 42"></div>
                    <div><label>좋아요 수</label><input id="gen-likes-single-count" type="number" min="1" value="10000"></div>
                </div>
            </div>

            <!-- Mode: 범위 지정 -->
            <div id="likes-mode-range" class="mode-panel">
                <p class="mode-desc">상품 ID 범위 또는 목록을 지정하여 좋아요를 생성합니다.</p>
                <div class="gen-config" style="grid-template-columns:1fr 1fr">
                    <div><label>상품 ID (콤마 구분 또는 범위)</label>
                        <input id="gen-likes-range-ids" placeholder="예: 1,2,3 또는 1-100">
                    </div>
                    <div><label>상품당 좋아요 수</label><input id="gen-likes-range-count" type="number" min="1" value="1000"></div>
                </div>
                <p style="font-size:11px;color:#94a3b8;margin-top:4px">형식: 1,2,3 (개별 지정) / 1-100 (범위 지정) / 혼합 가능: 1,5,10-20</p>
            </div>

            <div style="margin-top:12px;text-align:right">
                <button class="btn btn-success" id="gen-likes-btn">좋아요 생성</button>
            </div>
            <div class="progress-bar" id="gen-likes-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-likes-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Order Generation -->
        <div class="gen-section">
            <h3>5. Order 생성</h3>
            <p class="mode-desc">가상 유저들이 주문을 생성합니다. (포인트 자동 충전 → 정상 주문 플로우)</p>
            <div class="mode-tabs" id="order-mode-tabs">
                <button class="mode-tab active" data-mode="single">단일 상품 주문</button>
                <button class="mode-tab" data-mode="multi">다중 상품 주문</button>
                <button class="mode-tab" data-mode="random">랜덤 상품 주문</button>
            </div>

            <!-- Mode: 단일 상품 -->
            <div id="order-mode-single" class="mode-panel active">
                <p class="mode-desc">모든 유저가 동일한 하나의 상품을 주문합니다.</p>
                <div class="gen-config">
                    <div><label>상품 선택</label><select id="gen-order-single-product" style="width:100%"></select></div>
                    <div><label>수량</label><input id="gen-order-single-qty" type="number" min="1" value="1"></div>
                </div>
            </div>

            <!-- Mode: 다중 상품 -->
            <div id="order-mode-multi" class="mode-panel">
                <p class="mode-desc">모든 유저가 지정한 여러 상품을 한 주문에 담습니다.</p>
                <div id="gen-order-multi-items">
                    <div class="order-item-row" style="display:flex;gap:8px;margin-bottom:8px;align-items:end">
                        <div style="flex:3"><label>상품</label><select class="order-multi-product" style="width:100%"></select></div>
                        <div style="flex:1"><label>수량</label><input class="order-multi-qty" type="number" min="1" value="1" style="width:100%"></div>
                        <div><button class="btn btn-danger btn-sm order-multi-remove" style="margin-bottom:2px">X</button></div>
                    </div>
                </div>
                <button class="btn btn-secondary btn-sm" id="gen-order-multi-add" style="margin-top:4px">+ 상품 추가</button>
            </div>

            <!-- Mode: 랜덤 -->
            <div id="order-mode-random" class="mode-panel">
                <p class="mode-desc">유저별로 랜덤 상품을 선택해 주문합니다.</p>
                <div class="gen-config">
                    <div><label>주문당 아이템 수</label><input id="gen-order-random-items" type="number" min="1" value="3"></div>
                </div>
            </div>

            <div style="margin-top:12px" class="gen-config">
                <div><label>유저당 주문 수</label><input id="gen-order-per-user" type="number" min="1" value="1"></div>
                <div><label>대상 유저 수 (최대)</label><input id="gen-order-max-users" type="number" min="1" value="100"></div>
                <div style="display:flex;align-items:end"><button class="btn btn-primary" id="gen-order-btn">주문 생성</button></div>
            </div>
            <div class="progress-bar" id="gen-order-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-order-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Coupon Generation -->
        <div class="gen-section">
            <h3>6. Coupon 생성</h3>
            <p class="mode-desc">쿠폰을 대량 생성하고, 선택 시 전체 유저에게 발급합니다.</p>
            <div class="gen-config">
                <div><label>쿠폰 수</label><input id="gen-coupon-count" type="number" min="1" value="5"></div>
                <div><label>할인 타입</label>
                    <select id="gen-coupon-type">
                        <option value="FIXED">정액 (FIXED)</option>
                        <option value="RATE">정률 (RATE)</option>
                    </select>
                </div>
                <div><label>할인 값</label><input id="gen-coupon-value" type="number" min="1" value="5000"></div>
                <div><label>최소 주문 금액</label><input id="gen-coupon-min-order" type="number" min="0" value="10000"></div>
            </div>
            <div class="gen-config" style="margin-top:8px">
                <div><label>쿠폰당 총 수량</label><input id="gen-coupon-qty" type="number" min="1" value="10000"></div>
                <div style="display:flex;align-items:center;gap:8px;padding-top:20px">
                    <label style="display:flex;align-items:center;gap:6px;cursor:pointer">
                        <input type="checkbox" id="gen-coupon-issue-all" checked>
                        전체 유저에게 발급
                    </label>
                </div>
                <div style="display:flex;align-items:end"><button class="btn btn-primary" id="gen-coupon-btn">쿠폰 생성</button></div>
            </div>
            <div class="progress-bar" id="gen-coupon-progress-wrap" style="display:none">
                <div class="progress-fill" id="gen-coupon-progress" style="width:0%">0%</div>
            </div>
        </div>

        <!-- Stop -->
        <div style="margin-bottom:16px">
            <button class="btn btn-danger" id="gen-stop-btn" style="display:none">생성 중지</button>
        </div>

        <!-- Log -->
        <div class="panel">
            <div class="panel-header">
                <h2>실행 로그</h2>
                <button class="btn btn-secondary btn-sm" onclick="document.getElementById('gen-log').innerHTML=''">Clear</button>
            </div>
            <div class="log-area" id="gen-log"></div>
        </div>`;

    // Mode tab switching
    setupModeTabs('product-mode-tabs', 'product-mode');
    setupModeTabs('likes-mode-tabs', 'likes-mode');
    setupModeTabs('order-mode-tabs', 'order-mode');

    // Button events
    document.getElementById('gen-user-btn').addEventListener('click', generateUsers);
    document.getElementById('gen-brand-btn').addEventListener('click', generateBrands);
    document.getElementById('gen-product-btn').addEventListener('click', generateProducts);
    document.getElementById('gen-likes-btn').addEventListener('click', generateLikes);
    document.getElementById('gen-order-btn').addEventListener('click', generateOrders);
    document.getElementById('gen-coupon-btn').addEventListener('click', generateCoupons);
    document.getElementById('gen-stop-btn').addEventListener('click', () => { shouldStop = true; });

    // Order multi-product add/remove
    document.getElementById('gen-order-multi-add').addEventListener('click', addOrderMultiRow);
    document.getElementById('gen-order-multi-items').addEventListener('click', (e) => {
        if (e.target.classList.contains('order-multi-remove')) {
            const rows = document.querySelectorAll('#gen-order-multi-items .order-item-row');
            if (rows.length > 1) e.target.closest('.order-item-row').remove();
        }
    });

    await loadBrandsCache();
    await loadProductsCache();
    await refreshStats();
}

// === Mode Tab Switching ===
function setupModeTabs(tabsId, panelPrefix) {
    const container = document.getElementById(tabsId);
    container.querySelectorAll('.mode-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            container.querySelectorAll('.mode-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            document.querySelectorAll(`[id^="${panelPrefix}-"]`).forEach(p => p.classList.remove('active'));
            document.getElementById(`${panelPrefix}-${tab.dataset.mode}`).classList.add('active');
        });
    });
}

function getActiveMode(tabsId) {
    return document.querySelector(`#${tabsId} .mode-tab.active`).dataset.mode;
}

// === Brand Cache ===
async function loadBrandsCache() {
    try {
        const d = await BrandApi.list(0, 500);
        cachedBrands = d.items;
        populateBrandSelectors();
    } catch (e) { log(`브랜드 로드 실패: ${e.message}`, 'error'); }
}

function populateBrandSelectors() {
    // Single brand select
    const sel = document.getElementById('gen-prod-single-brand');
    if (sel) {
        sel.innerHTML = cachedBrands.map(b => `<option value="${b.id}">${b.name} (ID:${b.id})</option>`).join('');
    }
    // Multi brand checklist
    const checklist = document.getElementById('gen-prod-multi-brands');
    if (checklist) {
        if (cachedBrands.length === 0) {
            checklist.innerHTML = '<p style="color:#94a3b8;font-size:13px">브랜드가 없습니다. 먼저 생성해주세요.</p>';
            return;
        }
        checklist.innerHTML = `
            <div style="margin-bottom:8px">
                <button class="btn btn-secondary btn-sm" id="brand-check-all">전체 선택</button>
                <button class="btn btn-secondary btn-sm" id="brand-uncheck-all">전체 해제</button>
                <span style="font-size:12px;color:#64748b;margin-left:8px" id="brand-check-count">0개 선택</span>
            </div>
            <div class="checkbox-grid">${cachedBrands.map(b =>
                `<label class="checkbox-item"><input type="checkbox" value="${b.id}" class="brand-cb"> ${b.name}</label>`
            ).join('')}</div>`;

        document.getElementById('brand-check-all').addEventListener('click', () => {
            checklist.querySelectorAll('.brand-cb').forEach(cb => cb.checked = true);
            updateBrandCheckCount();
        });
        document.getElementById('brand-uncheck-all').addEventListener('click', () => {
            checklist.querySelectorAll('.brand-cb').forEach(cb => cb.checked = false);
            updateBrandCheckCount();
        });
        checklist.querySelectorAll('.brand-cb').forEach(cb =>
            cb.addEventListener('change', updateBrandCheckCount));
    }
}

function updateBrandCheckCount() {
    const count = document.querySelectorAll('.brand-cb:checked').length;
    const el = document.getElementById('brand-check-count');
    if (el) el.textContent = `${count}개 선택`;
}

function getSelectedBrandIds() {
    return Array.from(document.querySelectorAll('.brand-cb:checked')).map(cb => +cb.value);
}

// === Product Cache (for Order section) ===
async function loadProductsCache() {
    try {
        const d = await ProductApi.list(0, 200);
        cachedProducts = d.items || [];
        populateOrderProductSelectors();
    } catch (e) { log(`상품 로드 실패: ${e.message}`, 'error'); }
}

function populateOrderProductSelectors() {
    const options = cachedProducts.length === 0
        ? '<option value="">상품 없음</option>'
        : cachedProducts.map(p =>
            `<option value="${p.id}">${p.name} (₩${p.price.toLocaleString()}, 재고:${p.stock})</option>`
        ).join('');

    const singleSel = document.getElementById('gen-order-single-product');
    if (singleSel) singleSel.innerHTML = options;

    document.querySelectorAll('.order-multi-product').forEach(sel => sel.innerHTML = options);
}

function addOrderMultiRow() {
    const container = document.getElementById('gen-order-multi-items');
    const options = cachedProducts.length === 0
        ? '<option value="">상품 없음</option>'
        : cachedProducts.map(p =>
            `<option value="${p.id}">${p.name} (₩${p.price.toLocaleString()}, 재고:${p.stock})</option>`
        ).join('');

    const row = document.createElement('div');
    row.className = 'order-item-row';
    row.style.cssText = 'display:flex;gap:8px;margin-bottom:8px;align-items:end';
    row.innerHTML = `
        <div style="flex:3"><label>상품</label><select class="order-multi-product" style="width:100%">${options}</select></div>
        <div style="flex:1"><label>수량</label><input class="order-multi-qty" type="number" min="1" value="1" style="width:100%"></div>
        <div><button class="btn btn-danger btn-sm order-multi-remove" style="margin-bottom:2px">X</button></div>`;
    container.appendChild(row);
}

// === Stats ===
async function refreshStats() {
    try {
        const stats = await DataGenApi.stats();
        document.getElementById('gen-stats').innerHTML = `
            <div class="stat-card"><div class="label">Users</div><div class="value">${stats.userCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Brands</div><div class="value">${stats.brandCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Products</div><div class="value">${stats.productCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Likes</div><div class="value">${stats.likeCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Orders</div><div class="value">${stats.orderCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Coupons</div><div class="value">${stats.couponCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">Issued</div><div class="value">${stats.ownedCouponCount.toLocaleString()}</div></div>`;
    } catch (e) { log(`Stats 에러: ${e.message}`, 'error'); }
}

// ==============================
// User Generation
// ==============================
async function generateUsers() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const prefix = document.getElementById('gen-user-prefix').value || 'testuser';
    const count = +document.getElementById('gen-user-count').value;
    const defaultPoint = +document.getElementById('gen-user-point').value;
    if (count <= 0) return;

    startRun('gen-user-btn');
    showProgress('gen-user-progress-wrap');
    log(`유저 ${count}명 생성 시작 (접두사: ${prefix}, 기본 포인트: ${defaultPoint.toLocaleString()})...`);

    const t = Date.now();
    try {
        updateProgress('gen-user-progress', 50, 100);
        const result = await DataGenApi.generateUsers(prefix, count, defaultPoint);
        updateProgress('gen-user-progress', 100, 100);
        log(`완료: ${result.message} (${elapsed(t)})`, 'success');
    } catch (e) {
        log(`유저 생성 실패: ${e.message}`, 'error');
    }

    endRun('gen-user-btn');
    await refreshStats();
}

// ==============================
// Brand Generation
// ==============================
async function generateBrands() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const count = +document.getElementById('gen-brand-count').value;
    const prefix = document.getElementById('gen-brand-prefix').value || 'Brand';
    if (count <= 0) return;

    startRun('gen-brand-btn');
    showProgress('gen-brand-progress-wrap');
    log(`브랜드 ${count}개 생성 시작...`);

    let created = 0, failed = 0;
    const t = Date.now();

    for (let i = 1; i <= count; i++) {
        if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
        try { await BrandApi.create(`${prefix}_${Date.now()}_${i}`); created++; }
        catch (e) { failed++; }
        updateProgress('gen-brand-progress', i, count);
    }

    log(`완료: ${created}개 생성, ${failed}개 실패 (${elapsed(t)})`, 'success');
    endRun('gen-brand-btn');
    await loadBrandsCache();
    await refreshStats();
}

// ==============================
// Product Generation
// ==============================
async function generateProducts() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const mode = getActiveMode('product-mode-tabs');
    const stockMin = +document.getElementById('gen-prod-stockmin').value;
    const stockMax = +document.getElementById('gen-prod-stockmax').value;

    let tasks = []; // [{brandId, count, priceMin, priceMax}]

    if (mode === 'all') {
        const total = +document.getElementById('gen-prod-all-count').value;
        const priceMin = +document.getElementById('gen-prod-all-pricemin').value;
        const priceMax = +document.getElementById('gen-prod-all-pricemax').value;
        if (total <= 0) return;
        if (cachedBrands.length === 0) { Toast.error('브랜드가 없습니다. 먼저 생성해주세요.'); return; }
        const perBrand = Math.ceil(total / cachedBrands.length);
        let remaining = total;
        for (const b of cachedBrands) {
            const cnt = Math.min(perBrand, remaining);
            if (cnt <= 0) break;
            tasks.push({ brandId: b.id, brandName: b.name, count: cnt, priceMin, priceMax });
            remaining -= cnt;
        }
    } else if (mode === 'single') {
        const brandId = +document.getElementById('gen-prod-single-brand').value;
        const count = +document.getElementById('gen-prod-single-count').value;
        const priceMin = +document.getElementById('gen-prod-single-pricemin').value;
        const priceMax = +document.getElementById('gen-prod-single-pricemax').value;
        if (!brandId || count <= 0) return;
        const brand = cachedBrands.find(b => b.id === brandId);
        tasks.push({ brandId, brandName: brand?.name || `ID:${brandId}`, count, priceMin, priceMax });
    } else if (mode === 'multi') {
        const brandIds = getSelectedBrandIds();
        const countPerBrand = +document.getElementById('gen-prod-multi-count').value;
        const priceMin = +document.getElementById('gen-prod-multi-pricemin').value;
        const priceMax = +document.getElementById('gen-prod-multi-pricemax').value;
        if (brandIds.length === 0) { Toast.error('브랜드를 선택해주세요.'); return; }
        if (countPerBrand <= 0) return;
        for (const id of brandIds) {
            const brand = cachedBrands.find(b => b.id === id);
            tasks.push({ brandId: id, brandName: brand?.name || `ID:${id}`, count: countPerBrand, priceMin, priceMax });
        }
    }

    const totalCount = tasks.reduce((sum, t) => sum + t.count, 0);
    if (totalCount === 0) return;

    startRun('gen-product-btn');
    showProgress('gen-product-progress-wrap');
    log(`상품 생성 시작 (모드: ${modeLabel('product', mode)}, 총 ${totalCount.toLocaleString()}개, ${tasks.length}개 브랜드)`);

    let created = 0, failed = 0, done = 0;
    const t = Date.now();
    const concurrency = 10;

    for (const task of tasks) {
        if (shouldStop) break;
        log(`  → ${task.brandName}: ${task.count}개 생성 중...`);

        for (let i = 0; i < task.count; i += concurrency) {
            if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
            const batch = [];
            for (let j = i; j < Math.min(i + concurrency, task.count); j++) {
                const price = Math.round(randInt(task.priceMin, task.priceMax) / 100) * 100;
                batch.push(
                    ProductApi.create({
                        brandId: task.brandId,
                        name: `Product_${task.brandName}_${j + 1}_${randStr(4)}`,
                        price,
                        stock: randInt(stockMin, stockMax),
                    }).then(() => { created++; }).catch(() => { failed++; })
                );
            }
            await Promise.all(batch);
            done += batch.length;
            updateProgress('gen-product-progress', done, totalCount);

            if (done % 500 === 0) {
                log(`  진행: ${done.toLocaleString()}/${totalCount.toLocaleString()} (성공: ${created}, 실패: ${failed})`);
            }
        }
    }

    log(`완료: ${created.toLocaleString()}개 생성, ${failed}개 실패 (${elapsed(t)})`, 'success');
    endRun('gen-product-btn');
    await refreshStats();
}

// ==============================
// Like Generation
// ==============================
async function generateLikes() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const mode = getActiveMode('likes-mode-tabs');

    if (mode === 'all') {
        await generateLikesAll();
    } else if (mode === 'single') {
        await generateLikesSingle();
    } else if (mode === 'range') {
        await generateLikesRange();
    }
}

async function generateLikesAll() {
    const likesPerProduct = +document.getElementById('gen-likes-all-per').value;
    const batchSize = +document.getElementById('gen-likes-all-batch').value;
    if (likesPerProduct <= 0 || batchSize <= 0) return;

    startRun('gen-likes-btn');
    showProgress('gen-likes-progress-wrap');

    let stats;
    try { stats = await DataGenApi.stats(); } catch (e) { Toast.error(e.message); endRun('gen-likes-btn'); return; }
    if (stats.productCount === 0) { Toast.error('상품이 없습니다.'); endRun('gen-likes-btn'); return; }

    const total = stats.productCount;
    log(`전체 상품 ${total.toLocaleString()}개에 각 ~${likesPerProduct}개 좋아요 생성 시작...`);

    let totalCreated = 0, processed = 0;
    const t = Date.now();
    const totalPages = Math.ceil(total / batchSize);

    for (let page = 0; page < totalPages; page++) {
        if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
        try {
            const productPage = await ProductApi.list(page, batchSize);
            const productIds = productPage.items.map(p => p.id);
            if (productIds.length === 0) break;

            const randomLikes = Math.max(1, likesPerProduct + randInt(
                -Math.floor(likesPerProduct * 0.3), Math.floor(likesPerProduct * 0.3)));
            const result = await DataGenApi.generateLikes(productIds, randomLikes);
            totalCreated += result.totalCreated;
            processed += productIds.length;
        } catch (e) {
            log(`배치 ${page + 1} 에러: ${e.message}`, 'error');
            processed += batchSize;
        }
        updateProgress('gen-likes-progress', processed, total);
        if ((page + 1) % 10 === 0) log(`  진행: ${processed.toLocaleString()}/${total.toLocaleString()} 상품, ${totalCreated.toLocaleString()} 좋아요`);
    }

    log(`완료: ${totalCreated.toLocaleString()}개 좋아요 생성 (${elapsed(t)})`, 'success');
    endRun('gen-likes-btn');
    await refreshStats();
}

async function generateLikesSingle() {
    const productId = +document.getElementById('gen-likes-single-id').value;
    const count = +document.getElementById('gen-likes-single-count').value;
    if (!productId || count <= 0) { Toast.error('상품 ID와 좋아요 수를 입력해주세요.'); return; }

    startRun('gen-likes-btn');
    showProgress('gen-likes-progress-wrap');
    log(`상품 #${productId}에 좋아요 ${count.toLocaleString()}개 생성 시작...`);

    const t = Date.now();
    const batchSize = 1000;
    let totalCreated = 0;

    for (let offset = 0; offset < count; offset += batchSize) {
        if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
        const thisBatch = Math.min(batchSize, count - offset);
        try {
            const result = await DataGenApi.generateLikes([productId], thisBatch);
            totalCreated += result.totalCreated;
        } catch (e) { log(`에러: ${e.message}`, 'error'); }
        updateProgress('gen-likes-progress', offset + thisBatch, count);
    }

    log(`완료: 상품 #${productId}에 ${totalCreated.toLocaleString()}개 좋아요 생성 (${elapsed(t)})`, 'success');
    endRun('gen-likes-btn');
    await refreshStats();
}

async function generateLikesRange() {
    const input = document.getElementById('gen-likes-range-ids').value.trim();
    const countPer = +document.getElementById('gen-likes-range-count').value;
    if (!input || countPer <= 0) { Toast.error('상품 ID와 좋아요 수를 입력해주세요.'); return; }

    const productIds = parseIdRange(input);
    if (productIds.length === 0) { Toast.error('유효한 상품 ID를 입력해주세요.'); return; }

    startRun('gen-likes-btn');
    showProgress('gen-likes-progress-wrap');
    log(`상품 ${productIds.length}개에 각 ${countPer.toLocaleString()}개 좋아요 생성 시작... (IDs: ${productIds.length <= 10 ? productIds.join(',') : productIds.slice(0, 10).join(',') + '...'})`);

    const t = Date.now();
    let totalCreated = 0;
    const batchSize = 500;

    for (let i = 0; i < productIds.length; i += batchSize) {
        if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
        const chunk = productIds.slice(i, i + batchSize);
        try {
            const result = await DataGenApi.generateLikes(chunk, countPer);
            totalCreated += result.totalCreated;
        } catch (e) { log(`배치 에러: ${e.message}`, 'error'); }
        updateProgress('gen-likes-progress', Math.min(i + batchSize, productIds.length), productIds.length);
    }

    log(`완료: ${totalCreated.toLocaleString()}개 좋아요 생성 (${elapsed(t)})`, 'success');
    endRun('gen-likes-btn');
    await refreshStats();
}

// ==============================
// Order Generation
// ==============================
async function generateOrders() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const mode = getActiveMode('order-mode-tabs');
    const ordersPerUser = +document.getElementById('gen-order-per-user').value;
    const maxUsers = +document.getElementById('gen-order-max-users').value;
    if (ordersPerUser <= 0 || maxUsers <= 0) return;

    // Build request payload based on mode
    let items = null;
    let itemsPerOrder = null;
    let requestMode = 'random';
    let modeDesc = '';

    if (mode === 'single') {
        const productId = +document.getElementById('gen-order-single-product').value;
        const qty = +document.getElementById('gen-order-single-qty').value;
        if (!productId) { log('상품을 선택해주세요.', 'error'); return; }
        items = [{ productId, quantity: qty }];
        requestMode = 'specific';
        const product = cachedProducts.find(p => p.id === productId);
        modeDesc = `단일 상품 (${product?.name || 'ID:' + productId}, 수량:${qty})`;
    } else if (mode === 'multi') {
        const rows = document.querySelectorAll('#gen-order-multi-items .order-item-row');
        items = [];
        for (const row of rows) {
            const productId = +row.querySelector('.order-multi-product').value;
            const qty = +row.querySelector('.order-multi-qty').value;
            if (productId && qty > 0) items.push({ productId, quantity: qty });
        }
        if (items.length === 0) { log('상품을 추가해주세요.', 'error'); return; }
        requestMode = 'specific';
        modeDesc = `다중 상품 (${items.length}개 상품)`;
    } else {
        itemsPerOrder = +document.getElementById('gen-order-random-items').value || 3;
        requestMode = 'random';
        modeDesc = `랜덤 상품 (주문당 ${itemsPerOrder}개)`;
    }

    startRun('gen-order-btn');
    showProgress('gen-order-progress-wrap');
    log(`주문 생성 시작 — ${modeDesc}, 유저당 ${ordersPerUser}건, 최대 ${maxUsers}명`);

    const t = Date.now();
    let totalCreated = 0, totalFailed = 0;

    try {
        const userData = await UserApi.list(0, maxUsers);
        const users = userData.items || [];
        if (users.length === 0) { log('유저가 없습니다. 먼저 유저를 생성해주세요.', 'error'); endRun('gen-order-btn'); return; }

        log(`  대상 유저: ${users.length}명`);
        const totalOrders = users.length * ordersPerUser;
        let processed = 0;
        const batchSize = 10;

        for (let i = 0; i < users.length; i += batchSize) {
            if (shouldStop) { log('사용자에 의해 중지됨', 'warn'); break; }
            const batch = users.slice(i, i + batchSize);

            for (let round = 0; round < ordersPerUser; round++) {
                if (shouldStop) break;
                const userIds = batch.map(u => u.id);
                const body = { userIds, mode: requestMode };
                if (requestMode === 'specific') body.items = items;
                if (requestMode === 'random') body.itemsPerOrder = itemsPerOrder;

                try {
                    const result = await DataGenApi.generateOrders(body);
                    totalCreated += result.totalCreated;
                    totalFailed += result.totalFailed;
                } catch (e) {
                    log(`배치 에러: ${e.message}`, 'error');
                    totalFailed += userIds.length;
                }
                processed += batch.length;
                updateProgress('gen-order-progress', processed, totalOrders);
            }

            if ((i + batchSize) % 50 === 0 || i + batchSize >= users.length) {
                log(`  진행: ${processed}/${totalOrders} (성공: ${totalCreated}, 실패: ${totalFailed})`);
            }
        }
    } catch (e) {
        log(`주문 생성 에러: ${e.message}`, 'error');
    }

    log(`완료: ${totalCreated}건 생성, ${totalFailed}건 실패 (${elapsed(t)})`, 'success');
    endRun('gen-order-btn');
    await refreshStats();
}

// ==============================
// Coupon Generation
// ==============================
async function generateCoupons() {
    if (isRunning) { Toast.error('이미 실행 중입니다'); return; }
    const count = +document.getElementById('gen-coupon-count').value;
    const discountType = document.getElementById('gen-coupon-type').value;
    const discountValue = +document.getElementById('gen-coupon-value').value;
    const minOrderAmount = +document.getElementById('gen-coupon-min-order').value || null;
    const totalQuantityPerCoupon = +document.getElementById('gen-coupon-qty').value;
    const issueToAllUsers = document.getElementById('gen-coupon-issue-all').checked;

    if (count <= 0 || discountValue <= 0 || totalQuantityPerCoupon <= 0) return;

    startRun('gen-coupon-btn');
    showProgress('gen-coupon-progress-wrap');
    log(`쿠폰 ${count}개 생성 시작 (${discountType} ${discountValue}${discountType === 'RATE' ? '%' : '원'}, 전체 발급: ${issueToAllUsers ? 'O' : 'X'})...`);

    const t = Date.now();
    try {
        updateProgress('gen-coupon-progress', 30, 100);
        const result = await DataGenApi.generateCoupons({
            count, discountType, discountValue,
            minOrderAmount, totalQuantityPerCoupon, issueToAllUsers
        });
        updateProgress('gen-coupon-progress', 100, 100);
        log(`완료: ${result.message} (${elapsed(t)})`, 'success');
    } catch (e) {
        log(`쿠폰 생성 실패: ${e.message}`, 'error');
    }

    endRun('gen-coupon-btn');
    await refreshStats();
}

// === ID Range Parser ===
// "1,2,3" → [1,2,3]
// "1-5" → [1,2,3,4,5]
// "1,3,10-15" → [1,3,10,11,12,13,14,15]
function parseIdRange(input) {
    const ids = new Set();
    input.split(',').forEach(part => {
        part = part.trim();
        if (part.includes('-')) {
            const [start, end] = part.split('-').map(Number);
            if (!isNaN(start) && !isNaN(end)) {
                for (let i = Math.min(start, end); i <= Math.max(start, end); i++) ids.add(i);
            }
        } else {
            const n = Number(part);
            if (!isNaN(n) && n > 0) ids.add(n);
        }
    });
    return Array.from(ids).sort((a, b) => a - b);
}

// === Helpers ===
function startRun(btnId) {
    isRunning = true; shouldStop = false;
    document.getElementById(btnId).disabled = true;
    document.getElementById('gen-stop-btn').style.display = 'inline-flex';
}
function endRun(btnId) {
    isRunning = false; shouldStop = false;
    document.getElementById(btnId).disabled = false;
    document.getElementById('gen-stop-btn').style.display = 'none';
}
function showProgress(wrapId) { document.getElementById(wrapId).style.display = 'block'; }
function updateProgress(fillId, current, total) {
    const pct = Math.round((current / total) * 100);
    const el = document.getElementById(fillId);
    el.style.width = pct + '%';
    el.textContent = pct + '%';
}
function log(msg, type = '') {
    const el = document.getElementById('gen-log');
    if (!el) return;
    const cls = type ? ` class="log-${type}"` : '';
    el.innerHTML += `<div${cls}>[${new Date().toLocaleTimeString('ko-KR')}] ${msg}</div>`;
    el.scrollTop = el.scrollHeight;
}
function elapsed(start) { return ((Date.now() - start) / 1000).toFixed(1) + 's'; }
function randInt(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }
function randStr(len) { return Math.random().toString(36).substring(2, 2 + len); }
function modeLabel(section, mode) {
    const labels = {
        product: { all: '전체 분산', single: '특정 브랜드', multi: '브랜드별 지정' },
        likes: { all: '전체 분산', single: '특정 상품', range: '범위 지정' },
        order: { single: '단일 상품', multi: '다중 상품', random: '랜덤 상품' },
    };
    return labels[section]?.[mode] || mode;
}

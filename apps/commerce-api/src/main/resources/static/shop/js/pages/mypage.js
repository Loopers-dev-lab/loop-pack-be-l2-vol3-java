import { OrderApi, LikeApi, CouponApi, UserApi, ProductApi } from '../api.js';
import { Auth } from '../auth.js';

let currentSection = 'orders';

export async function initMyPage() {
    const app = document.getElementById('app');

    let userInfo = { name: Auth.getName(), email: '', point: 0 };
    try {
        const me = await UserApi.me();
        userInfo = me;
    } catch { /* ignore */ }

    app.innerHTML = `
        <div class="mypage-layout">
            <div class="mypage-sidebar">
                <div class="user-info">
                    <div class="name">${esc(userInfo.name || Auth.get().loginId)}</div>
                    <div class="email">${esc(userInfo.email || '')}</div>
                    <div style="margin-top:8px;font-size:18px;font-weight:800;color:#6366f1">${(userInfo.point || 0).toLocaleString()}P</div>
                </div>
                <div class="mypage-menu-item active" data-section="orders">주문 내역</div>
                <div class="mypage-menu-item" data-section="likes">좋아요 목록</div>
                <div class="mypage-menu-item" data-section="coupons">내 쿠폰</div>
                <div class="mypage-menu-item" data-section="point">포인트 충전</div>
                <div class="mypage-menu-item" data-section="profile">계정 설정</div>
            </div>
            <div class="mypage-content" id="mypage-content"></div>
        </div>`;

    app.querySelectorAll('.mypage-menu-item').forEach(item => {
        item.addEventListener('click', () => {
            app.querySelectorAll('.mypage-menu-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            currentSection = item.dataset.section;
            loadSection(currentSection);
        });
    });

    await loadSection(currentSection);
}

async function loadSection(section) {
    const content = document.getElementById('mypage-content');
    switch (section) {
        case 'orders': return loadOrders(content);
        case 'likes': return loadLikes(content);
        case 'coupons': return loadCoupons(content);
        case 'point': return loadPoint(content);
        case 'profile': return loadProfile(content);
    }
}

// === Orders ===
async function loadOrders(el) {
    el.innerHTML = '<h2>주문 내역</h2><p style="color:#94a3b8">불러오는 중...</p>';
    try {
        const data = await OrderApi.list();
        const items = data.items || [];
        if (items.length === 0) {
            el.innerHTML = '<h2>주문 내역</h2><div class="empty-state"><p>주문 내역이 없습니다</p></div>';
            return;
        }
        el.innerHTML = `
            <h2>주문 내역</h2>
            <div class="table-wrap"><table>
                <thead><tr><th>주문 ID</th><th>결제 금액</th><th>할인</th><th>상태</th><th>주문일</th><th></th></tr></thead>
                <tbody>${items.map(o => `
                    <tr>
                        <td>#${o.orderId}</td>
                        <td><strong>${o.totalPrice.toLocaleString()}원</strong></td>
                        <td>${o.discountAmount.toLocaleString()}원</td>
                        <td><span class="badge ${statusBadge(o.status)}">${statusLabel(o.status)}</span></td>
                        <td>${formatDate(o.createdAt)}</td>
                        <td><button class="btn btn-sm btn-outline" data-order-id="${o.orderId}">상세</button></td>
                    </tr>`).join('')}
                </tbody>
            </table></div>`;

        el.querySelectorAll('[data-order-id]').forEach(btn => {
            btn.addEventListener('click', () => showOrderDetail(+btn.dataset.orderId));
        });
    } catch (e) { el.innerHTML = `<h2>주문 내역</h2><p style="color:#dc2626">${esc(e.message)}</p>`; }
}

async function showOrderDetail(orderId) {
    try {
        const o = await OrderApi.get(orderId);
        const itemRows = (o.items || []).map(i => `
            <tr>
                <td>${esc(i.productName)}</td>
                <td>${esc(i.brandName)}</td>
                <td>${i.orderPrice.toLocaleString()}원</td>
                <td>${i.quantity}개</td>
                <td>${o.status !== 'CANCELLED' ? `<button class="btn btn-sm btn-danger cancel-item-btn" data-oid="${o.orderId}" data-iid="${i.orderItemId}">취소</button>` : '-'}</td>
            </tr>`).join('');

        Modal.open(`주문 #${o.orderId} 상세`, `
            <div style="margin-bottom:16px">
                <span class="badge ${statusBadge(o.status)}">${statusLabel(o.status)}</span>
                <span style="margin-left:12px;font-weight:600">${o.totalPrice.toLocaleString()}원</span>
            </div>
            <div class="table-wrap"><table>
                <thead><tr><th>상품</th><th>브랜드</th><th>가격</th><th>수량</th><th></th></tr></thead>
                <tbody>${itemRows}</tbody>
            </table></div>`);

        document.querySelectorAll('.cancel-item-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                try {
                    await OrderApi.cancelItem(+btn.dataset.oid, +btn.dataset.iid);
                    Toast.success('항목이 취소되었습니다');
                    Modal.close();
                    loadOrders(document.getElementById('mypage-content'));
                } catch (e) { Toast.error(e.message); }
            });
        });
    } catch (e) { Toast.error(e.message); }
}

// === Likes ===
async function loadLikes(el) {
    el.innerHTML = '<h2>좋아요 목록</h2><p style="color:#94a3b8">불러오는 중...</p>';
    try {
        const data = await LikeApi.myList();
        const items = data.items || [];
        if (items.length === 0) {
            el.innerHTML = '<h2>좋아요 목록</h2><div class="empty-state"><p>좋아요한 상품이 없습니다</p></div>';
            return;
        }

        // 상품 정보를 로드
        const productInfos = await Promise.allSettled(
            items.map(l => ProductApi.get(l.productId))
        );

        const rows = items.map((l, idx) => {
            const pResult = productInfos[idx];
            const p = pResult.status === 'fulfilled' ? pResult.value : null;
            return `
                <tr>
                    <td>${p ? `<a href="#product/${p.id}" style="color:#6366f1;font-weight:600">${esc(p.name)}</a>` : `상품 #${l.productId}`}</td>
                    <td>${p ? esc(p.brandName) : '-'}</td>
                    <td>${p ? p.price.toLocaleString() + '원' : '-'}</td>
                    <td>${formatDate(l.createdAt)}</td>
                    <td><button class="btn btn-sm btn-danger unlike-btn" data-pid="${l.productId}">취소</button></td>
                </tr>`;
        }).join('');

        el.innerHTML = `
            <h2>좋아요 목록 (${items.length})</h2>
            <div class="table-wrap"><table>
                <thead><tr><th>상품명</th><th>브랜드</th><th>가격</th><th>좋아요 일시</th><th></th></tr></thead>
                <tbody>${rows}</tbody>
            </table></div>`;

        el.querySelectorAll('.unlike-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                try {
                    await LikeApi.remove(+btn.dataset.pid);
                    Toast.success('좋아요를 취소했습니다');
                    loadLikes(el);
                } catch (e) { Toast.error(e.message); }
            });
        });
    } catch (e) { el.innerHTML = `<h2>좋아요 목록</h2><p style="color:#dc2626">${esc(e.message)}</p>`; }
}

// === Coupons ===
async function loadCoupons(el) {
    el.innerHTML = '<h2>내 쿠폰</h2><p style="color:#94a3b8">불러오는 중...</p>';
    try {
        const data = await CouponApi.myList();
        const items = data.items || [];
        if (items.length === 0) {
            el.innerHTML = '<h2>내 쿠폰</h2><div class="empty-state"><p>보유한 쿠폰이 없습니다</p></div>';
            return;
        }

        const rows = items.map(c => `
            <tr>
                <td><strong>${esc(c.couponName)}</strong></td>
                <td><span class="badge badge-blue">${c.discountType === 'RATE' ? c.discountValue + '% 할인' : c.discountValue.toLocaleString() + '원 할인'}</span></td>
                <td>${c.minOrderAmount ? c.minOrderAmount.toLocaleString() + '원 이상' : '없음'}</td>
                <td><span class="badge ${c.status === 'USED' ? 'badge-gray' : 'badge-green'}">${couponStatusLabel(c.status)}</span></td>
                <td>${formatDate(c.expiredAt)}</td>
            </tr>`).join('');

        el.innerHTML = `
            <h2>내 쿠폰 (${items.length})</h2>
            <div class="table-wrap"><table>
                <thead><tr><th>쿠폰명</th><th>할인</th><th>최소 주문</th><th>상태</th><th>만료일</th></tr></thead>
                <tbody>${rows}</tbody>
            </table></div>`;
    } catch (e) { el.innerHTML = `<h2>내 쿠폰</h2><p style="color:#dc2626">${esc(e.message)}</p>`; }
}

// === Point ===
async function loadPoint(el) {
    el.innerHTML = '<h2>포인트 충전</h2><p style="color:#94a3b8">불러오는 중...</p>';
    try {
        const data = await UserApi.getPoint();
        el.innerHTML = `
            <h2>포인트 충전</h2>
            <div style="text-align:center;padding:24px;background:#eef2ff;border-radius:12px;margin-bottom:24px">
                <div style="font-size:14px;color:#64748b;margin-bottom:4px">현재 보유 포인트</div>
                <div id="current-point" style="font-size:36px;font-weight:800;color:#6366f1">${data.point.toLocaleString()}P</div>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
                <button class="btn btn-outline quick-charge" data-amount="10000">+10,000P</button>
                <button class="btn btn-outline quick-charge" data-amount="50000">+50,000P</button>
                <button class="btn btn-outline quick-charge" data-amount="100000">+100,000P</button>
                <button class="btn btn-outline quick-charge" data-amount="500000">+500,000P</button>
                <button class="btn btn-outline quick-charge" data-amount="1000000">+1,000,000P</button>
            </div>
            <div style="display:flex;gap:8px;align-items:end">
                <div class="form-group" style="flex:1;margin-bottom:0">
                    <label>직접 입력</label>
                    <input id="charge-amount" type="number" min="1" placeholder="충전할 포인트" value="100000">
                </div>
                <button class="btn btn-primary" id="charge-btn" style="height:42px">충전</button>
            </div>`;

        el.querySelectorAll('.quick-charge').forEach(btn => {
            btn.addEventListener('click', () => chargePoint(+btn.dataset.amount));
        });
        document.getElementById('charge-btn').addEventListener('click', () => {
            const amount = +document.getElementById('charge-amount').value;
            if (amount > 0) chargePoint(amount);
            else Toast.error('금액을 입력해주세요');
        });
    } catch (e) { el.innerHTML = `<h2>포인트 충전</h2><p style="color:#dc2626">${esc(e.message)}</p>`; }
}

async function chargePoint(amount) {
    try {
        const data = await UserApi.chargePoint(amount);
        document.getElementById('current-point').textContent = data.point.toLocaleString() + 'P';
        // 사이드바 포인트도 업데이트
        const sidebar = document.querySelector('.mypage-sidebar .user-info');
        const pointEl = sidebar.querySelector('div:last-child');
        if (pointEl) pointEl.textContent = data.point.toLocaleString() + 'P';
        Toast.success(`${amount.toLocaleString()}P 충전 완료!`);
    } catch (e) { Toast.error(e.message); }
}

// === Profile ===
async function loadProfile(el) {
    el.innerHTML = '<h2>계정 설정</h2><p style="color:#94a3b8">불러오는 중...</p>';
    try {
        const me = await UserApi.me();
        el.innerHTML = `
            <h2>계정 설정</h2>
            <div style="display:grid;gap:16px;max-width:400px">
                <div class="form-group">
                    <label>아이디</label>
                    <input value="${esc(me.loginId)}" disabled style="background:#f1f5f9">
                </div>
                <div class="form-group">
                    <label>이름</label>
                    <input value="${esc(me.name)}" disabled style="background:#f1f5f9">
                </div>
                <div class="form-group">
                    <label>생년월일</label>
                    <input value="${esc(me.birthDate)}" disabled style="background:#f1f5f9">
                </div>
                <div class="form-group">
                    <label>이메일</label>
                    <input value="${esc(me.email)}" disabled style="background:#f1f5f9">
                </div>
                <hr style="border-color:#e5e7eb">
                <h3 style="font-size:16px">비밀번호 변경</h3>
                <div class="form-group">
                    <label>현재 비밀번호</label>
                    <input id="pw-current" type="password">
                </div>
                <div class="form-group">
                    <label>새 비밀번호</label>
                    <input id="pw-new" type="password">
                    <div class="form-hint">8~16자, 대/소문자+숫자+특수문자 포함</div>
                </div>
                <button class="btn btn-primary" id="pw-change-btn">비밀번호 변경</button>
            </div>`;

        document.getElementById('pw-change-btn').addEventListener('click', async () => {
            const current = document.getElementById('pw-current').value;
            const newPw = document.getElementById('pw-new').value;
            if (!current || !newPw) { Toast.error('비밀번호를 입력해주세요'); return; }
            try {
                await UserApi.changePassword({ currentPassword: current, newPassword: newPw });
                Toast.success('비밀번호가 변경되었습니다. 다시 로그인해주세요.');
                Auth.clear();
                location.hash = 'login';
            } catch (e) { Toast.error(e.message); }
        });
    } catch (e) { el.innerHTML = `<h2>계정 설정</h2><p style="color:#dc2626">${esc(e.message)}</p>`; }
}

// === Helpers ===
function statusBadge(s) {
    if (s === 'COMPLETED' || s === 'CONFIRMED') return 'badge-green';
    if (s === 'CANCELLED') return 'badge-red';
    return 'badge-gray';
}
function statusLabel(s) {
    const map = { CREATED: '주문완료', COMPLETED: '처리완료', CONFIRMED: '확정', CANCELLED: '취소됨' };
    return map[s] || s;
}
function couponStatusLabel(s) {
    const map = { AVAILABLE: '사용 가능', ISSUED: '사용 가능', USED: '사용 완료', EXPIRED: '만료됨' };
    return map[s] || s;
}
function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

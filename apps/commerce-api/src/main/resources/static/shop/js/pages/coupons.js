import { CouponApi } from '../api.js';
import { Auth } from '../auth.js';

let currentPage = 0;
let myCouponIds = new Set();

export async function initCoupons() {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:24px">
            <h1 style="font-size:24px;font-weight:800">쿠폰 다운로드</h1>
            ${Auth.isLoggedIn()
                ? '<button class="btn btn-sm btn-outline" id="my-coupons-btn">내 쿠폰 보기</button>'
                : ''}
        </div>
        <div id="coupon-list"></div>
        <div class="pagination" id="coupon-pagination"></div>`;

    if (Auth.isLoggedIn()) {
        await loadMyCoupons();
        document.getElementById('my-coupons-btn').addEventListener('click', showMyCoupons);
    }

    await loadAvailableCoupons(0);
}

async function loadMyCoupons() {
    try {
        const data = await CouponApi.myList();
        myCouponIds = new Set((data.items || []).map(c => c.couponId));
    } catch { myCouponIds = new Set(); }
}

async function loadAvailableCoupons(page) {
    currentPage = page;
    const el = document.getElementById('coupon-list');

    try {
        const data = await CouponApi.available(page, 20);
        const now = new Date();
        const coupons = (data.items || []).filter(c => new Date(c.expiredAt) > now);

        if (coupons.length === 0) {
            el.innerHTML = '<div class="empty-state"><p>다운로드 가능한 쿠폰이 없습니다</p></div>';
            return;
        }

        el.innerHTML = `<div class="coupon-grid">${coupons.map(c => {
            const remaining = c.totalQuantity - c.issuedQuantity;
            const isOwned = myCouponIds.has(c.id);
            const isSoldOut = remaining <= 0;
            return `
                <div class="coupon-card ${isOwned ? 'owned' : ''} ${isSoldOut ? 'sold-out' : ''}">
                    <div class="coupon-card-left">
                        <div class="coupon-discount">
                            ${c.discountType === 'RATE'
                                ? `<span class="discount-value">${c.discountValue}%</span>`
                                : `<span class="discount-value">${c.discountValue.toLocaleString()}원</span>`}
                        </div>
                        <div class="coupon-type">${c.discountType === 'RATE' ? '정률 할인' : '정액 할인'}</div>
                    </div>
                    <div class="coupon-card-right">
                        <div class="coupon-name">${esc(c.name)}</div>
                        <div class="coupon-meta">
                            <span>잔여 ${remaining.toLocaleString()} / ${c.totalQuantity.toLocaleString()}장</span>
                            <span>~${formatDate(c.expiredAt)}</span>
                        </div>
                        <div class="coupon-action">
                            ${isOwned
                                ? '<span class="badge badge-green">다운로드 완료</span>'
                                : isSoldOut
                                    ? '<span class="badge badge-gray">소진됨</span>'
                                    : `<button class="btn btn-sm btn-primary coupon-download-btn" data-id="${c.id}" data-name="${esc(c.name)}">다운로드</button>`}
                        </div>
                    </div>
                </div>`;
        }).join('')}</div>`;

        el.querySelectorAll('.coupon-download-btn').forEach(btn => {
            btn.addEventListener('click', () => downloadCoupon(+btn.dataset.id, btn.dataset.name, btn));
        });

        renderPagination(data);
    } catch (e) { el.innerHTML = `<div class="empty-state"><p style="color:#dc2626">${esc(e.message)}</p></div>`; }
}

async function downloadCoupon(couponId, couponName, btn) {
    if (!Auth.isLoggedIn()) {
        Toast.error('로그인이 필요합니다');
        location.hash = 'login';
        return;
    }

    btn.disabled = true;
    btn.textContent = '처리 중...';

    try {
        await CouponApi.issue(couponId);
        myCouponIds.add(couponId);
        Toast.success(`"${couponName}" 쿠폰을 다운로드했습니다!`);
        // 버튼을 "다운로드 완료"로 교체
        const action = btn.closest('.coupon-action');
        action.innerHTML = '<span class="badge badge-green">다운로드 완료</span>';
        btn.closest('.coupon-card').classList.add('owned');
    } catch (e) {
        Toast.error(e.message);
        btn.disabled = false;
        btn.textContent = '다운로드';
    }
}

async function showMyCoupons() {
    try {
        const data = await CouponApi.myList();
        const items = data.items || [];

        if (items.length === 0) {
            Modal.open('내 쿠폰', '<div class="empty-state"><p>보유한 쿠폰이 없습니다</p></div>');
            return;
        }

        const rows = items.map(c => `
            <div class="my-coupon-item">
                <div class="my-coupon-left">
                    <div class="coupon-discount-sm">
                        ${c.discountType === 'RATE'
                            ? `${c.discountValue}%`
                            : `${c.discountValue.toLocaleString()}원`}
                    </div>
                </div>
                <div class="my-coupon-right">
                    <div style="font-weight:600">${esc(c.couponName)}</div>
                    <div style="font-size:13px;color:#64748b">
                        ${c.minOrderAmount ? c.minOrderAmount.toLocaleString() + '원 이상 주문 시' : '조건 없음'}
                        &middot; ~${formatDate(c.expiredAt)}
                    </div>
                    <span class="badge ${couponStatusBadge(c.status)}">${couponStatusLabel(c.status)}</span>
                </div>
            </div>`).join('');

        Modal.open(`내 쿠폰 (${items.length}장)`, `<div class="my-coupon-list">${rows}</div>`);
    } catch (e) { Toast.error(e.message); }
}

function renderPagination(data) {
    const c = document.getElementById('coupon-pagination');
    if (data.totalPages <= 1) { c.innerHTML = ''; return; }
    c.innerHTML = `
        <button class="btn btn-sm btn-secondary" id="cpg-prev" ${data.page === 0 ? 'disabled' : ''}>이전</button>
        <span class="page-info">${data.page + 1} / ${data.totalPages}</span>
        <button class="btn btn-sm btn-secondary" id="cpg-next" ${data.page >= data.totalPages - 1 ? 'disabled' : ''}>다음</button>`;
    document.getElementById('cpg-prev').addEventListener('click', () => loadAvailableCoupons(data.page - 1));
    document.getElementById('cpg-next').addEventListener('click', () => loadAvailableCoupons(data.page + 1));
}

function couponStatusBadge(s) {
    if (s === 'AVAILABLE' || s === 'ISSUED') return 'badge-green';
    if (s === 'USED') return 'badge-gray';
    return 'badge-red';
}
function couponStatusLabel(s) {
    const map = { AVAILABLE: '사용 가능', ISSUED: '사용 가능', USED: '사용 완료', EXPIRED: '만료됨' };
    return map[s] || s;
}
function formatDate(d) { return d ? new Date(d).toLocaleDateString('ko-KR') : '-'; }
function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

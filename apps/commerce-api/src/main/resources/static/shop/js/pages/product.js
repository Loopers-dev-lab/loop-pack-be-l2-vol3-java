import { ProductApi, LikeApi, OrderApi, CouponApi } from '../api.js';
import { Auth } from '../auth.js';

export async function initProduct(params) {
    const app = document.getElementById('app');
    const id = params.id;
    if (!id) { location.hash = 'home'; return; }

    app.innerHTML = '<div class="empty-state"><p>불러오는 중...</p></div>';

    try {
        const p = await ProductApi.get(id);
        let liked = false;
        let myLikes = [];

        // 로그인 상태면 좋아요 여부 확인
        if (Auth.isLoggedIn()) {
            try {
                const data = await LikeApi.myList();
                myLikes = data.items || [];
                liked = myLikes.some(l => l.productId === p.id);
            } catch { /* ignore */ }
        }

        const mainImgs = p.mainImages || [];
        const detailImgs = p.detailImages || [];

        app.innerHTML = `
            <div style="margin-bottom:16px">
                <a href="#home" class="btn btn-sm btn-outline">&larr; 상품 목록</a>
            </div>
            <div class="product-detail">
                <div class="product-detail-gallery">
                    <div class="gallery-main">
                        <img id="gallery-main-img" src="${esc(mainImgs.length ? mainImgs[0].imageUrl : (p.thumbnailUrl || ''))}" alt="${esc(p.name)}">
                    </div>
                    ${mainImgs.length > 1 ? `
                    <div class="gallery-thumbs">
                        ${mainImgs.map((img, i) => `
                            <div class="gallery-thumb ${i === 0 ? 'active' : ''}" data-url="${esc(img.imageUrl)}">
                                <img src="${esc(img.imageUrl)}" alt="이미지 ${i + 1}">
                            </div>`).join('')}
                    </div>` : ''}
                </div>
                <div class="product-detail-info">
                    <div class="product-detail-brand">${esc(p.brandName)}</div>
                    <h1>${esc(p.name)}</h1>
                    <div class="product-detail-meta">
                        <span>재고: ${p.stock.toLocaleString()}개</span>
                        <span id="like-count">&hearts; ${p.likeCount.toLocaleString()}</span>
                    </div>
                    <div class="product-detail-price">${p.price.toLocaleString()}원</div>
                    <div class="product-detail-actions">
                        <button class="btn btn-like ${liked ? 'liked' : ''}" id="like-btn">
                            ${liked ? '♥ 좋아요 취소' : '♡ 좋아요'}
                        </button>
                        <button class="btn btn-primary btn-lg" id="order-btn">주문하기</button>
                    </div>
                    <div id="order-section"></div>
                </div>
            </div>
            ${detailImgs.length ? `
            <div class="product-detail-section">
                <h2>상품 상세</h2>
                <div class="detail-images">
                    ${detailImgs.map(img => `
                        <img src="${esc(img.imageUrl)}" alt="상품 상세 이미지">`).join('')}
                </div>
            </div>` : ''}`;

        // Gallery thumbnail click
        document.querySelectorAll('.gallery-thumb').forEach(thumb => {
            thumb.addEventListener('click', () => {
                document.getElementById('gallery-main-img').src = thumb.dataset.url;
                document.querySelectorAll('.gallery-thumb').forEach(t => t.classList.remove('active'));
                thumb.classList.add('active');
            });
        });

        // Like toggle
        document.getElementById('like-btn').addEventListener('click', async () => {
            if (!Auth.isLoggedIn()) { Toast.error('로그인이 필요합니다'); location.hash = 'login'; return; }
            try {
                if (liked) {
                    await LikeApi.remove(p.id);
                    liked = false;
                    p.likeCount = Math.max(0, p.likeCount - 1);
                    Toast.success('좋아요를 취소했습니다');
                } else {
                    await LikeApi.add(p.id);
                    liked = true;
                    p.likeCount++;
                    Toast.success('좋아요를 눌렀습니다');
                }
                document.getElementById('like-btn').className = `btn btn-like ${liked ? 'liked' : ''}`;
                document.getElementById('like-btn').innerHTML = liked ? '♥ 좋아요 취소' : '♡ 좋아요';
                document.getElementById('like-count').innerHTML = `&hearts; ${p.likeCount.toLocaleString()}`;
            } catch (e) { Toast.error(e.message); }
        });

        // Order form
        document.getElementById('order-btn').addEventListener('click', () => {
            if (!Auth.isLoggedIn()) { Toast.error('로그인이 필요합니다'); location.hash = 'login'; return; }
            showOrderForm(p);
        });

    } catch (e) {
        app.innerHTML = `<div class="empty-state"><p>상품을 불러올 수 없습니다: ${esc(e.message)}</p></div>`;
    }
}

async function showOrderForm(product) {
    const section = document.getElementById('order-section');
    let quantity = 1;
    let coupons = [];

    try {
        const data = await CouponApi.myList();
        coupons = (data.items || []).filter(c => c.status === 'AVAILABLE' || c.status === 'ISSUED');
    } catch { /* ignore */ }

    const couponOptions = coupons.length
        ? coupons.map(c => `<option value="${c.ownedCouponId}" data-type="${c.discountType}" data-value="${c.discountValue}" data-min="${c.minOrderAmount}">${esc(c.couponName)} (${c.discountType === 'RATE' ? c.discountValue + '%' : c.discountValue.toLocaleString() + '원'} 할인)</option>`).join('')
        : '';

    function render() {
        const total = product.price * quantity;
        const sel = section.querySelector('#order-coupon');
        let discount = 0;
        let couponId = null;

        if (sel && sel.value) {
            const opt = sel.selectedOptions[0];
            couponId = +sel.value;
            const type = opt.dataset.type;
            const value = +opt.dataset.value;
            const min = +opt.dataset.min;
            if (total >= min) {
                discount = type === 'RATE' ? Math.floor(total * value / 100) : value;
                discount = Math.min(discount, total);
            }
        }

        section.querySelector('#qty-display').textContent = quantity;
        section.querySelector('#price-original').textContent = total.toLocaleString() + '원';
        section.querySelector('#price-discount').textContent = '-' + discount.toLocaleString() + '원';
        section.querySelector('#price-total').textContent = (total - discount).toLocaleString() + '원';
    }

    section.innerHTML = `
        <div class="order-form">
            <h3>주문 정보</h3>
            <div class="quantity-control">
                <span style="font-size:14px;color:#64748b">수량</span>
                <button id="qty-minus">-</button>
                <span id="qty-display">${quantity}</span>
                <button id="qty-plus">+</button>
            </div>
            ${coupons.length ? `
            <div class="coupon-select">
                <label style="font-size:13px;font-weight:600;color:#475569;margin-bottom:6px;display:block">쿠폰 적용</label>
                <select id="order-coupon">
                    <option value="">쿠폰 미적용</option>
                    ${couponOptions}
                </select>
            </div>` : ''}
            <div class="price-summary">
                <div class="price-row"><span>상품 금액</span><span id="price-original">${(product.price * quantity).toLocaleString()}원</span></div>
                <div class="price-row"><span>할인 금액</span><span id="price-discount">-0원</span></div>
                <div class="price-row total"><span>결제 금액</span><span id="price-total">${(product.price * quantity).toLocaleString()}원</span></div>
            </div>
            <button class="btn btn-primary btn-lg" style="width:100%" id="place-order-btn">주문하기</button>
        </div>`;

    section.querySelector('#qty-minus').addEventListener('click', () => {
        if (quantity > 1) { quantity--; render(); }
    });
    section.querySelector('#qty-plus').addEventListener('click', () => {
        quantity++;
        render();
    });
    if (section.querySelector('#order-coupon')) {
        section.querySelector('#order-coupon').addEventListener('change', render);
    }

    section.querySelector('#place-order-btn').addEventListener('click', async () => {
        const btn = section.querySelector('#place-order-btn');
        btn.disabled = true;
        btn.textContent = '주문 처리 중...';
        try {
            const body = {
                items: [{ productId: product.id, quantity, expectedPrice: product.price }],
            };
            const couponSel = section.querySelector('#order-coupon');
            if (couponSel && couponSel.value) {
                body.couponId = +couponSel.value;
            }
            await OrderApi.create(body);
            Toast.success('주문이 완료되었습니다!');
            section.innerHTML = `
                <div style="margin-top:24px;padding:24px;background:#ecfdf5;border-radius:12px;text-align:center">
                    <div style="font-size:32px;margin-bottom:8px">✓</div>
                    <p style="font-weight:600;color:#059669">주문이 완료되었습니다</p>
                    <a href="#mypage" class="btn btn-sm btn-primary" style="margin-top:12px">주문 내역 보기</a>
                </div>`;
        } catch (e) {
            Toast.error(e.message);
            btn.disabled = false;
            btn.textContent = '주문하기';
        }
    });
}

function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

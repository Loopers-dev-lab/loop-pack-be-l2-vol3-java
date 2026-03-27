
import { ProductApi, LikeApi } from '../api.js';
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

function showOrderForm(product) {
    const section = document.getElementById('order-section');
    const orderBtn = document.getElementById('order-btn');
    let quantity = 1;

    // 원래 "주문하기" 버튼 숨김
    orderBtn.style.display = 'none';

    section.innerHTML = `
        <div class="order-form">
            <div class="quantity-control">
                <span style="font-size:13px;color:#999;letter-spacing:0.5px">QTY</span>
                <button id="qty-minus">-</button>
                <span id="qty-display">${quantity}</span>
                <button id="qty-plus">+</button>
            </div>
            <button class="btn btn-primary btn-lg" style="width:100%" id="place-order-btn">주문하기</button>
        </div>`;

    section.querySelector('#qty-minus').addEventListener('click', () => {
        if (quantity > 1) {
            quantity--;
            section.querySelector('#qty-display').textContent = quantity;
        }
    });
    section.querySelector('#qty-plus').addEventListener('click', () => {
        quantity++;
        section.querySelector('#qty-display').textContent = quantity;
    });

    section.querySelector('#place-order-btn').addEventListener('click', () => {
        // Order를 DB에 생성하지 않고, 주문 정보를 sessionStorage에 저장 후 결제 페이지로 이동
        const checkoutData = {
            items: [{ productId: product.id, quantity, expectedPrice: product.price }],
            product: {
                id: product.id,
                name: product.name,
                brandName: product.brandName,
                price: product.price,
            },
        };
        sessionStorage.setItem('checkoutData', JSON.stringify(checkoutData));
        location.href = `/shop/pg-checkout.html?mode=new&returnUrl=${encodeURIComponent('/shop/index.html#product/' + product.id)}`;
    });
}

// bfcache 복원 시 UI 정리
window.addEventListener('pageshow', (e) => {
    if (e.persisted) {
        const section = document.getElementById('order-section');
        const orderBtn = document.getElementById('order-btn');
        if (orderBtn) orderBtn.style.display = '';
        if (section) section.innerHTML = '';
    }
});


function esc(s) { return (s || '').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

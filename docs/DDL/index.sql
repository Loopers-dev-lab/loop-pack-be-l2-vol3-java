-- =============================================================================
-- INDEX  (07-index-cache-strategy.md 기준)
-- =============================================================================

-- [CRITICAL] 만료 배치 조회 (OrderExpiryScheduler)
CREATE INDEX idx_orders_status_expires_at
    ON orders (status, expires_at);

-- [CRITICAL] 사용자별 주문 목록 조회
CREATE INDEX idx_orders_user_id_created_at
    ON orders (user_id, created_at DESC);

-- [HIGH] 고객 상품 목록 필터 (del_yn='N' AND display_status='ACTIVE' AND sale_status='ON_SALE')
CREATE INDEX idx_products_del_yn_display_sale
    ON products (del_yn, display_status, sale_status);

-- [HIGH] 브랜드별 상품 조회
CREATE INDEX idx_products_brand_id
    ON products (brand_id, del_yn);

-- [HIGH] 통계 쿼리 — 주문 상태별 건수, 일별 통계
CREATE INDEX idx_orders_del_yn_created_at
    ON orders (del_yn, created_at);

-- [HIGH] 사용자별 상태 건수 카운트
CREATE INDEX idx_orders_user_id_status
    ON orders (user_id, status);

-- [MEDIUM] 사용자 좋아요 목록
CREATE INDEX idx_likes_user_id
    ON likes (user_id);

-- [MEDIUM] 상품별 좋아요 집계, 통계 JOIN
CREATE INDEX idx_likes_product_id
    ON likes (product_id);

-- [MEDIUM] 사용자 장바구니 조회
CREATE INDEX idx_cart_items_user_id
    ON cart_items (user_id);

-- [MEDIUM] 상품별 주문 집계 (통계)
CREATE INDEX idx_order_items_product_id
    ON order_items (product_id);

-- [MEDIUM] 브랜드 목록 조회
CREATE INDEX idx_brands_del_yn_display
    ON brands (del_yn, display_status);

-- NOTE: users.login_id 는 UNIQUE KEY 로 이미 인덱스 포함
-- NOTE: product_stocks 는 PK 직접 조회(CAS UPDATE)만 하므로 추가 인덱스 불필요
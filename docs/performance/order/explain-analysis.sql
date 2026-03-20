-- =============================================================
-- 주문 도메인 EXPLAIN 분석 — 9가지 시나리오
-- =============================================================
-- 환경: MySQL 8.0, orders 500,000건, order_item 1,500,000건
-- 상태 분포: DELIVERED 54%, CANCELLED 29%, SHIPPING 9%, PAID 5%, PENDING 2%
-- 유저 10,000명 (파레토), 상품 5,000개
--
-- 인덱스:
--   idx_orders_user_status_created (user_id, status, created_at DESC)
--   idx_orders_user_created        (user_id, created_at DESC)           ← 방어
--   idx_orders_status_created      (status, created_at DESC)
--   idx_order_item_product         (product_id, order_id)              ← covering
-- =============================================================


-- =====================
-- AS-IS: 인덱스 없는 상태 (기준선)
-- =====================
-- 실행 전 인덱스 제거:
-- DROP INDEX idx_orders_user_status_created ON orders;
-- DROP INDEX idx_orders_user_created ON orders;
-- DROP INDEX idx_orders_status_created ON orders;
-- DROP INDEX idx_order_item_product ON order_item;

-- [시나리오 1] 사용자 주문 목록 (인덱스 없음)
EXPLAIN SELECT * FROM orders
        WHERE user_id = 50 AND status = 'SHIPPING'
          AND created_at BETWEEN '2026-03-01' AND '2026-03-13'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=ALL | key=NULL | rows=498,012 | filtered=0.11% | Using where; Using filesort

-- [시나리오 2] 관리자 상태별 주문 목록 (인덱스 없음)
EXPLAIN SELECT * FROM orders
        WHERE status = 'SHIPPING'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=ALL | key=NULL | rows=498,012 | filtered=10.0% | Using where; Using filesort

-- [시나리오 3] 상품별 주문 내역 (인덱스 없음)
EXPLAIN SELECT o.* FROM orders o
                            JOIN order_item oi ON o.id = oi.order_id
        WHERE oi.product_id = 100
        ORDER BY o.created_at DESC LIMIT 20;
-- 실측: oi → type=ALL | key=NULL | rows=1,492,800 | Using where; Using temporary; Using filesort
--       o  → type=eq_ref | key=PRIMARY | rows=1


-- =====================
-- TO-BE: 인덱스 적용 후
-- =====================
-- 인덱스 복구:
-- CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at DESC);
-- CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
-- CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
-- CREATE INDEX idx_order_item_product ON order_item(product_id, order_id);

-- [시나리오 4] 사용자 주문 — status 있음 (인덱스 최적)
EXPLAIN SELECT * FROM orders
        WHERE user_id = 50 AND status = 'SHIPPING'
          AND created_at BETWEEN '2026-03-01' AND '2026-03-13'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=range | key=idx_orders_user_status_created | rows=17 | filtered=100.0% | Using index condition

-- [시나리오 5] 사용자 주문 — status 없음, 후보 B만 (중간 컬럼 skip)
-- idx_orders_user_created를 DROP한 상태에서 실행
EXPLAIN SELECT * FROM orders
        WHERE user_id = 50
          AND created_at BETWEEN '2026-03-01' AND '2026-03-13'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=ref | key=idx_orders_user_status_created | ref=const | rows=1,535 | filtered=11.11% | Using index condition; Using filesort
-- 분석: status 컬럼을 건너뛰므로 created_at 정렬 불가 → filesort 발생

-- [시나리오 6] 사용자 주문 — status 없음, 후보 A+B (방어 인덱스)
-- idx_orders_user_created 있는 상태에서 실행
EXPLAIN SELECT * FROM orders
        WHERE user_id = 50
          AND created_at BETWEEN '2026-03-01' AND '2026-03-13'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=range | key=idx_orders_user_created | rows=186 | filtered=100.0% | Using index condition
-- 분석: 옵티마이저가 후보 A 인덱스를 자동 선택, filesort 제거


-- =====================
-- 데이터 분포 영향: 상태별 성능 양극화
-- =====================

-- [시나리오 7] 관리자 SHIPPING (소수 상태 — 46,673건, 9%)
EXPLAIN SELECT * FROM orders
        WHERE status = 'SHIPPING'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=ref | key=idx_orders_status_created | ref=const | rows=90,062 | filtered=100.0%
-- 참고: EXPLAIN rows는 옵티마이저 추정값이며 실제 건수(46,673)와 차이 있음

-- [시나리오 8] 관리자 DELIVERED (다수 상태 — 272,052건, 54%)
EXPLAIN SELECT * FROM orders
        WHERE status = 'DELIVERED'
        ORDER BY created_at DESC LIMIT 20;
-- 실측: type=ref | key=idx_orders_status_created | ref=const | rows=249,006 | filtered=100.0%
-- 분석: 같은 인덱스인데 SHIPPING 대비 rows 3배. 데이터 분포가 인덱스 효과를 좌우함.
--       단, LIMIT 20 + 인덱스 정렬 순서 보장 → Early Termination으로 실제 20건만 읽고 중단.


-- =====================
-- Covering Index 확인
-- =====================

-- [시나리오 9] 상품별 주문 내역 (covering index)
EXPLAIN SELECT o.* FROM orders o
                            JOIN order_item oi ON o.id = oi.order_id
        WHERE oi.product_id = 100
        ORDER BY o.created_at DESC LIMIT 20;
-- 실측: oi → type=ref | key=idx_order_item_product | rows=324 | Using index; Using temporary; Using filesort
--       o  → type=eq_ref | key=PRIMARY | rows=1
-- 분석: order_item은 Using index(covering) → 테이블 접근 없이 인덱스만으로 처리
--       orders의 created_at 정렬은 두 테이블 간 정렬이라 인덱스로 해결 불가 → filesort 잔존
--       324건 filesort는 실행 시간에 거의 영향 없으므로 수용

-- Covering index 단독 확인
EXPLAIN SELECT oi.order_id FROM order_item oi WHERE oi.product_id = 100;
-- 실측: type=ref | key=idx_order_item_product | rows=324 | Extra=Using index
-- 분석: (product_id, order_id) 인덱스에 order_id가 포함되어 테이블 접근 불필요
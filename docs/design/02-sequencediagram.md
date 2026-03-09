### 1. 바로 주문 (상품 상세에서 주문서 생성 + 재고 예약)
**왜 이 다이어그램이 필요한가:**
주문서 생성과 hold가 **한 트랜잭션**이며, 조건부 UPDATE(CAS)로 오버셀을 막는 흐름을 보여준다.

```mermaid

sequenceDiagram
  autonumber
  actor U as User/Client
  participant PC as ProductService
  participant OF as OrderFacade/Controller
  participant OS as OrderService
  participant SS as StockService
  participant DB as Database

  Note over U,DB: 1) 상품 상세/목록 조회 → 바로 주문(주문서 생성 + 재고예약 Hold)\n전략: DB SoT + 조건부 UPDATE(CAS) + 단일 트랜잭션

  U->>PC: GET /api/v1/products/{productId}
  PC->>DB: SELECT products + brands + product_stocks\nWHERE product_id=:productId
  DB-->>PC: product detail + stock
  PC-->>U: 200 OK (product detail)

  U->>OF: POST /api/v1/orders (orderType=DIRECT, items=[{productId, qty}])
  OF->>DB: BEGIN TRANSACTION

  OF->>PC: validateProductOrderable(productId, qty)
  PC->>DB: SELECT p.*, b.*, s.on_hand, s.reserved\nFROM products p JOIN brands b JOIN product_stocks s ...
  DB-->>PC: product/brand/stock state
  Note over PC: 검증 기준\n- p.del_yn='N', b.del_yn='N'\n- p.display_status='ACTIVE', b.display_status='ACTIVE'\n- p.sale_status='ON_SALE'\n- (s.on_hand - s.reserved) >= qty
  PC-->>OF: valid or unavailableReason

  alt 상품 검증 실패 (DELETED/HIDDEN/STOPPED/TEMP_SOLD_OUT/OUT_OF_STOCK)
    OF->>DB: ROLLBACK
    OF-->>U: 409 Conflict (unavailableReason)
  else 검증 성공
    OF->>OS: createOrder(status=PENDING_PAYMENT, expires_at, order_type=DIRECT)
    OS->>DB: INSERT INTO orders(...)
    DB-->>OS: order_id
    OS-->>OF: order_id

    OF->>OS: createOrderItemsSnapshot(order_id, items)
    OS->>DB: INSERT INTO order_items(snapshot fields...)
    DB-->>OS: OK

    OF->>SS: hold(product_id, qty)
    SS->>DB: UPDATE product_stocks\nSET reserved = reserved + :qty\nWHERE product_id = :productId\n  AND del_yn='N'\n  AND (on_hand - reserved) >= :qty

    alt affectedRows == 1 (예약 성공)
      DB-->>SS: 1 row updated
      SS-->>OF: hold success
      OF->>DB: COMMIT
      OF-->>U: 201 Created (order_id, expires_at, status=PENDING_PAYMENT)
    else affectedRows == 0 (재고 부족/비정상)
      DB-->>SS: 0 row updated
      SS-->>OF: hold failed (OUT_OF_STOCK)
      OF->>DB: ROLLBACK
      OF-->>U: 409 Conflict (OUT_OF_STOCK)
    end
  end
  
```

---

### 2. 장바구니 선택 주문(다건) -> 데드락 방지
**왜 이 다이어그램이 필요한가:**
장바구니 선택 주문의 전체 흐름(조회 → 주문 생성 → 다건 hold)을 보여주고, 다건 재고 예약에서 **정렬 기반 데드락 방지**와 **부분 성공 금지**를 검증한다.

```mermaid
sequenceDiagram
  autonumber
  actor U as User/Client
  participant CartC as CartController
  participant CS as CartService
  participant OF as OrderFacade
  participant OS as OrderService
  participant SS as StockService
  participant DB as Database

  Note over U,DB: 2) 장바구니 선택 주문(다건) → 상품ID 정렬 → 재고예약 Hold\n전략: 조건부 UPDATE(CAS) + 단일 트랜잭션 + 부분 성공 금지

  U->>CartC: GET /api/v1/cart
  CartC->>CS: getCart(user)
  CS->>DB: SELECT cart_items + latest products/brands/product_stocks\n(가용여부 계산용)
  DB-->>CS: cart with availability
  CS-->>CartC: cart DTOs (available, unavailableReason 포함)
  CartC-->>U: 200 OK

  U->>OF: POST /api/v1/orders (orderType=CART, selectedCartItemIds)
  OF->>DB: BEGIN TRANSACTION

  OF->>CS: loadSelectedCartItems(user, selectedIds)
  CS->>DB: SELECT cart_items WHERE user_id=:userId ...
  DB-->>CS: selected items
  CS-->>OF: items

  OF->>OF: 동일 product_id 수량 병합
  OF->>OF: items를 product_id ASC로 정렬 (데드락 예방)

  OF->>OS: createOrder(status=PENDING_PAYMENT, expires_at, order_type=CART)
  OS->>DB: INSERT INTO orders(...)
  DB-->>OS: order_id
  OS-->>OF: order_id

  OF->>OS: createOrderItemsSnapshot(order_id, items)
  OS->>DB: INSERT INTO order_items(snapshot...)
  DB-->>OS: OK

  loop for each item (sorted)
    OF->>SS: hold(product_id, qty)
    SS->>DB: UPDATE product_stocks\nSET reserved = reserved + :qty\nWHERE product_id = :productId\n  AND del_yn='N'\n  AND (on_hand - reserved) >= :qty
    alt hold 성공 (affectedRows=1)
      DB-->>SS: OK
      SS-->>OF: hold ok
    else hold 실패 (affectedRows=0)
      DB-->>SS: NO ROW UPDATED
      SS-->>OF: hold failed (OUT_OF_STOCK / NOT_SALEABLE)
      Note over OF,DB: 실패 시 전체 롤백 (부분 성공 금지)
      OF->>DB: ROLLBACK
      OF-->>U: 409 Conflict (ORDER_NOT_CREATABLE)
    end
  end

  opt 모든 hold 성공한 경우에만
    OF->>DB: COMMIT
    OF-->>U: 201 Created (order_id, status=PENDING_PAYMENT)
  end
```

---

### 3. 주문 생성 시퀀스 다이어그램 (공통: DIRECT/CART)
**왜 이 다이어그램이 필요한가:**
재고 확인 → 주문서/스냅샷 저장 → 재고 예약(Hold)이 **단일 트랜잭션** 안에서 원자적으로 처리되어야 하며, 다건 주문 시 **데드락 방지(정렬)** 와 **부분 성공 금지**를 검증하기 위해 필요하다.

```mermaid
sequenceDiagram
  autonumber
  actor U as User/Client
  participant API as OrderController
  participant OF as OrderFacade
  participant OS as OrderService
  participant PS as ProductService
  participant SS as StockService
  participant CR as CartService
  participant DB as Database

  U->>API: POST /api/v1/orders<br/>{items OR selectedCartItemIds, orderType}
  API->>OF: createOrder(user, request)

  Note over OF,DB: === 단일 트랜잭션 시작 ===

  alt orderType = CART
    OF->>CR: loadSelectedCartItems(user, selectedIds)
    CR->>DB: SELECT cart_items WHERE user_id=:userId
    DB-->>CR: items
    CR-->>OF: items
  else orderType = DIRECT
    Note over OF: request.items 사용
  end

  OF->>PS: validateProducts(items)
  PS->>DB: SELECT p.*, b.*, s.on_hand, s.reserved\nFROM products p JOIN brands b JOIN product_stocks s ...
  DB-->>PS: product states
  Note over PS: 검증 기준\n- p.del_yn='N', b.del_yn='N'\n- p.display_status='ACTIVE', b.display_status='ACTIVE'\n- p.sale_status='ON_SALE'\n- available_qty=(s.on_hand-s.reserved)
  PS-->>OF: ok or fail(unavailableReason)

  alt 상품 검증 실패
    Note over OF,DB: === 트랜잭션 롤백 ===
    OF-->>API: 400/409 주문 불가 사유(unavailableReason)
    API-->>U: error
  end

  Note over OF: items 내 동일 product_id 합산 병합
  Note over OF: product_id 오름차순 정렬 (데드락 방지)

  OF->>OS: createOrder(status=PENDING_PAYMENT, expires_at, order_type)
  OS->>DB: INSERT INTO orders(...)
  DB-->>OS: order_id
  OS-->>OF: order_id

  OF->>OS: createOrderItemsSnapshot(order_id, items)
  OS->>DB: INSERT INTO order_items(snapshot...)
  DB-->>OS: OK

  loop 각 주문 항목 (product_id 오름차순)
    OF->>SS: hold(product_id, qty)
    SS->>DB: UPDATE product_stocks<br/>SET reserved = reserved + :qty<br/>WHERE product_id = :productId<br/>AND del_yn='N'<br/>AND (on_hand - reserved) >= :qty
    alt affectedRows = 0 (재고 부족)
      Note over OF,DB: === 트랜잭션 롤백 ===
      OF-->>API: 409 OUT_OF_STOCK
      API-->>U: error
      break
    else affectedRows = 1
      SS-->>OF: ok
    end
  end

  Note over OF,DB: === 트랜잭션 커밋 ===
  OF-->>API: 201 Created (order_id, status=PENDING_PAYMENT)
  API-->>U: order_id, status
```

---

### 4. 주문 취소 (PENDING_PAYMENT에서만)
**왜 이 다이어그램이 필요한가:**
결제 완료/만료 배치와 **경쟁 조건**이 발생할 수 있으므로, 취소는 **상태 CAS 전이**로 멱등하게 처리하고, 성공 시에만 재고 예약을 해제하며(부분 해제 금지), DIRECT 주문은 **장바구니 자동 복원**을 수행해야 한다.

```mermaid
sequenceDiagram
  autonumber
  actor U as User/Client
  participant API as OrderController
  participant OF as OrderFacade
  participant OS as OrderService
  participant SS as StockService
  participant CS as CartService
  participant DB as Database

  U->>API: POST /api/v1/orders/{orderId}/cancel
  API->>OF: cancelOrder(user, orderId)

  Note over OF,DB: === 단일 트랜잭션 시작 ===

  OF->>DB: UPDATE orders SET status='CANCELLED', updated_at=NOW()\nWHERE order_id=:orderId\n  AND user_id=:userId\n  AND status='PENDING_PAYMENT'\n  AND del_yn='N'
  alt affectedRows = 0
    OF->>DB: SELECT status, order_type FROM orders\nWHERE order_id=:orderId AND user_id=:userId AND del_yn='N'
    DB-->>OF: currentStatus
    alt currentStatus = CANCELLED
      Note over OF: 멱등 처리(이미 취소됨)
      OF-->>API: 200 OK (status=CANCELLED)
    else currentStatus = EXPIRED or PAID
      Note over OF: 취소 불가
      OF-->>API: 409 Conflict (NOT_CANCELLABLE)
    end
    API-->>U: response
  else affectedRows = 1
    OF->>OS: loadOrderItems(orderId)
    OS->>DB: SELECT order_items (product_id, quantity)\nWHERE order_id=:orderId AND del_yn='N'
    DB-->>OS: items
    OS-->>OF: items

    Note over OF: product_id 오름차순 정렬 (데드락 방지)
    loop 각 주문 항목 (product_id 오름차순)
      OF->>SS: release(product_id, qty)
      SS->>DB: UPDATE product_stocks<br/>SET reserved = reserved - :qty<br/>WHERE product_id = :productId<br/>AND del_yn='N'<br/>AND reserved >= :qty
      alt affectedRows = 0
        Note over OF,DB: === 트랜잭션 롤백 ===
        OF-->>API: 500 InconsistentReserved
        API-->>U: error
        break
      else affectedRows = 1
        SS-->>OF: ok
      end
    end

    alt order_type = DIRECT
      OF->>DB: INSERT INTO order_cart_restore(order_id, user_id, reason, trigger_source, restored_at)\nVALUES (:orderId, :userId, 'USER_CANCELLED', 'CANCEL_API', NOW())
      alt insert 성공 (처음 복원)
        OF->>CS: restoreToCart(order_id, user_id, items)
        CS->>DB: MERGE/UPSERT cart_items (수량 병합)
      else PK 충돌 (이미 복원됨)
        Note over OF: skip restore (멱등 처리)
      end
    end

    Note over OF,DB: === 트랜잭션 커밋 ===
    OF-->>API: 200 OK (status=CANCELLED)
    API-->>U: status
  end
```

---

### 5. 주문 만료 및 장바구니 복원 시퀀스 다이어그램
**왜 이 다이어그램이 필요한가:**
만료 배치와 장바구니 복원은 비동기적으로 일어나며, **경쟁 조건(결제 vs 만료)** 과 **멱등성(중복 복원 방지)** 을 검증해야 한다.

```mermaid
sequenceDiagram
    participant SCH as 만료 배치(Scheduler)
    participant OS as OrderService
    participant SS as StockService
    participant CS as CartService
    participant DB as Database

    SCH->>DB: SELECT orders<br/>WHERE status='PENDING_PAYMENT'<br/>AND del_yn='N'<br/>AND expires_at < NOW()<br/>LIMIT N

    loop 만료 대상 주문 건별
        Note over OS,DB: === 트랜잭션 시작 ===

        OS->>DB: UPDATE orders SET status='EXPIRED', updated_at=NOW()<br/>WHERE order_id=:orderId<br/>AND status='PENDING_PAYMENT'<br/>AND del_yn='N'<br/>AND expires_at < NOW()

        alt affectedRows = 0 (이미 전환됨)
            Note over OS: skip (CAS 실패 → 멱등 처리)
        else affectedRows = 1
            OS->>DB: SELECT order_type, user_id FROM orders WHERE order_id=:orderId
            DB-->>OS: order header
            OS->>DB: SELECT order_items(product_id, quantity)<br/>WHERE order_id=:orderId AND del_yn='N'
            DB-->>OS: items

            loop 각 주문 항목
                OS->>SS: releaseStock(product_id, qty)
                SS->>DB: UPDATE product_stocks<br/>SET reserved = reserved - :qty<br/>WHERE product_id = :productId<br/>AND del_yn='N'<br/>AND reserved >= :qty
            end

            alt order_type = DIRECT (바로 주문)
                OS->>DB: INSERT INTO order_cart_restore(order_id, user_id, reason, trigger_source, restored_at)<br/>VALUES (:orderId, :userId, 'EXPIRED', 'EXPIRE_JOB', NOW())
                alt insert 성공 (처음 복원)
                    OS->>CS: restoreToCart(order_id, user_id, items)
                    CS->>DB: MERGE/UPSERT cart_items (수량 병합)
                else PK 충돌
                    Note over OS: skip restore (이미 복원됨)
                end
            end
        end

        Note over OS,DB: === 트랜잭션 커밋 ===
    end
```

---

### 6. 상품/브랜드 검색 (고객용)
**목표:** 사용자는 키워드(q)로 브랜드/상품을 빠르게 찾을 수 있어야 한다.

```mermaid
sequenceDiagram
  autonumber
  actor U as User/Client
  participant QC as CatalogQueryController
  participant PQ as ProductQueryService
  participant BQ as BrandQueryService
  participant DB as RDBMS(DB)

  Note over U,DB: 고객용 검색은 /api/v1 prefix, q 파라미터(부분일치/대소문자 무시)\nRDBMS LIKE/FTS 중 택1(현재는 LIKE 가정), 성능 필요 시 인덱스/FTS 확장

  U->>QC: GET /api/v1/products?q=...&brandId=...&sort=latest&page=0&size=20
  QC->>PQ: listProducts(q, brandId, sort, page, size)
  PQ->>DB: SELECT products p JOIN brands b LEFT JOIN product_stocks s\nWHERE p.del_yn='N' AND b.del_yn='N'\nAND p.display_status='ACTIVE' AND b.display_status='ACTIVE'\nAND (p.product_name LIKE q OR b.brand_name LIKE q)\nAND brandId? ORDER BY ...
  DB-->>PQ: paged products
  PQ-->>QC: products DTOs (sale_status, stock 기반 품절표시 포함)
  QC-->>U: 200 OK (products)

  U->>QC: GET /api/v1/brands?q=...&page=0&size=20
  QC->>BQ: listBrands(q, page, size)
  BQ->>DB: SELECT brands\nWHERE del_yn='N' AND display_status='ACTIVE'\nAND brand_name LIKE q ORDER BY ...
  DB-->>BQ: paged brands
  BQ-->>QC: brands DTOs
  QC-->>U: 200 OK (brands)
```

---

### 7. 운영 통계 조회 (관리자 대시보드)
**목표:** 관리자는 기간 기준의 핵심 지표(주문/인기상품/재고)를 조회할 수 있어야 한다.

```mermaid
sequenceDiagram
  autonumber
  actor A as Admin
  participant AC as AdminStatsController
  participant AS as AdminStatsService
  participant DB as RDBMS(DB)

  Note over A,DB: 관리자 통계는 /api-admin/v1 prefix\nMVP는 RDBMS 집계 쿼리로 제공, 향후 이벤트/캐시로 확장 가능

  A->>AC: GET /api-admin/v1/stats/overview?startAt=...&endAt=...
  AC->>AS: getOverview(startAt, endAt)
  AS->>DB: SELECT COUNT(*) GROUP BY orders.status\nWHERE orders.del_yn='N' AND 기간조건
  DB-->>AS: order status counts
  AS->>DB: SELECT product_id, COUNT(*)/SUM(quantity)\nFROM order_items ... (기간조건) TOP N
  DB-->>AS: top ordered products
  AS->>DB: SELECT product_id, COUNT(*)\nFROM likes ... (기간조건) TOP N
  DB-->>AS: top liked products
  AS->>DB: SELECT product_id, on_hand, reserved, (on_hand-reserved) AS available\nFROM product_stocks\nWHERE del_yn='N' AND (on_hand-reserved) <= :threshold
  DB-->>AS: low stock list
  AS-->>AC: overview DTO
  AC-->>A: 200 OK (overview)
```

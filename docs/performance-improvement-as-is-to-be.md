# 성능 개선 과제 — AS-IS / TO-BE 분석

> 대규모 트래픽 환경 백엔드 성능 개선 과제(상품 목록·좋아요 정렬·캐시)에 대한 현재 상태(AS-IS)와 목표 상태(TO-BE) 정리.  
> 기준: `.docs/design/04-erd.md`, `AGENTS.md`, 실제 코드(ProductRepositoryImpl, LikeService, OrderFacade 등).

---

## 1. AS-IS (현재 상태)

### 1.1 상품 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/products?brandId=&sort=&page=&size=` |
| **구현** | `ProductFacade.getProductList` → `ProductService.findNotDeletedForList` → `ProductRepositoryImpl.findNotDeleted` |
| **정렬** | `latest`(createdAt DESC), `price_asc`, `price_desc`, `likes_desc` 지원 |
| **브랜드 필터** | `brandId` 선택 시 `product.brand_id = ?` 조건 적용 |
| **좋아요 순 정렬** | `LIKES_DESC` 시 `product` LEFT JOIN `likes` → `GROUP BY product.id` → `ORDER BY count(like.id) DESC` (인라인 집계, 비정규화 없음) |
| **인덱스** | ERD §4: `idx_product_brand_id (brand_id)` 권장. **복합 인덱스 (deleted_at, brand_id, created_at 등)는 미정의**. JPA 엔티티에 `@Table(indexes=...)` 없음. |
| **데이터 규모** | 10만 건 이상 상품 데이터 및 다양하게 분포한 컬럼 값에 대한 준비·벌크 삽입 전략 **미구현** |
| **성능 측정** | EXPLAIN 기반 인덱스 분석·전후 비교 **미수행** |

**참고 코드**: `ProductRepositoryImpl.findNotDeleted`, `findNotDeletedOrderByLikesDesc` (75–93라인) — likes_desc 시 매번 like 테이블과 조인·집계.

---

### 1.2 상품 상세 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/products/{productId}` |
| **구현** | `ProductFacade.getProductDetail` → product 조회 + brand 조회 + `likeService.getLikeCount(productId)` |
| **캐시** | **Redis 미적용** — 매 요청 DB 조회 + like count 쿼리 |
| **인덱스** | PK 조회 위주. `findByIdAndNotDeleted` 단일 조회라 인덱스 이슈는 제한적 |

---

### 1.3 주문 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/orders?start=&end=&page=&size=` (X-Loopers-LoginId로 userId 식별) |
| **구현** | `OrderFacade.findOrders` → `OrderRepositoryImpl.findByUserIdAndOrderedAtBetween` → `findByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc` |
| **조건** | `userId`, `orderedAt >= start`, `orderedAt < end`, `ORDER BY orderedAt DESC` |
| **인덱스** | ERD §4: `idx_order_user_id`, `idx_order_ordered_at` 권장. **복합 (user_id, ordered_at)** 인덱스는 코드/ERD에 명시 없음. |
| **취소 주문** | 취소 시 `order.status = CANCELLED`로만 변경. 목록 조회 시 **status 필터(예: CANCELLED 제외) 여부는 구현에 따라 다름** — “목록 인덱스가 꼬이지 않고 정상 필터링되는지 확인” 필요. |

---

### 1.4 주문 상세 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/orders/{orderId}` |
| **구현** | `findByIdWithOrderItems` (JOIN FETCH). 단건 조회. |
| **최적화** | 인덱스 최적화만 적용 가능(목록 대비 상대적으로 단순). |

---

### 1.5 좋아요 등록/취소

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/likes`, `DELETE /api/v1/likes` (또는 유사 경로) |
| **구현** | `LikeService.addLike` / `removeLike` — `likes` 테이블 **INSERT / DELETE 만** 수행 |
| **비정규화** | **product 테이블에 `like_count` 없음** → 좋아요 수는 매번 `like` 테이블 집계 |
| **count 동기화** | **없음**. 등록/취소 시 별도 count 갱신 로직 없음. |
| **캐시** | 상품/목록용 Redis 미적용이므로, 좋아요 변경 시 캐시 무효화도 **없음**. |

---

### 1.6 캐시

| 항목 | 내용 |
|------|------|
| **Redis** | `modules/redis` 설정 존재. **commerce-api 상품 상세/목록 API에는 Redis 캐시 미적용**. |
| **TTL / 캐시 키 / 무효화** | 미정의. |

---

### 1.7 상품 수정/삭제 API

| 항목 | 내용 |
|------|------|
| **수정** | `ProductFacade.updateProduct` — DB만 갱신. **캐시에 저장된 옛날 데이터 삭제(무효화) 로직 없음**. |
| **삭제** | `ProductFacade.deleteProduct` — soft delete만 수행. **관련 캐시 삭제 없음**. |

---

## 2. TO-BE (목표 상태) — 할일 기반 정리

### 2.1 상품 목록 조회 성능 개선

| 구분 | TO-BE 내용 |
|------|------------|
| **데이터** | 상품 10만 건 이상 준비. 각 컬럼(brand_id, name, price, stock_quantity, created_at 등) 값은 프로젝트 도메인·ERD 참고해 **다양하게 분포**하도록 준비 (벌크 삽입 스크립트/테스트 픽스처). |
| **기능** | 브랜드 필터 + 좋아요 순 정렬 유지. |
| **EXPLAIN** | 개선 전 쿼리에 **EXPLAIN** 수행 → 인덱스 최적화(복합 인덱스 등) 설계. |
| **인덱스** | 예: `(deleted_at, brand_id, created_at)`, `(deleted_at, brand_id, price)` 등 **정렬·필터 조건에 맞는 복합 인덱스** 추가. (실제 인덱스는 EXPLAIN 결과에 맞춰 결정.) |
| **성능 비교** | 개선 전/후 응답 시간 또는 쿼리 비용(EXPLAIN) 비교를 문서/테스트로 남김. |
| **트레이드오프** | 인덱스 추가 → 쓰기 부담·저장 공간 증가. 읽기 비중이 큰 목록 API에 유리한지 기준 정해 두기. |

---

### 2.2 좋아요 수 정렬 구조 개선

| 구분 | TO-BE 내용 |
|------|------------|
| **선택** | **비정규화(`product.like_count`)** 또는 **MaterializedView** 중 하나로 좋아요 수 정렬 성능 개선. |
| **비정규화** | `product` 테이블에 `like_count` 컬럼 추가. 목록/상세 조회 시 이 컬럼으로 정렬·노출. |
| **MaterializedView** | 상품+좋아요 집계 뷰를 주기적 또는 이벤트 기반 갱신. 정렬 시 뷰 조인 또는 뷰만 조회. |
| **동기화** | **좋아요 등록/취소 시 count 동기화 필수**. 비정규화 선택 시: `LikeService.addLike`/`removeLike` 내(또는 Facade)에서 해당 `product.like_count` +/- 1 갱신. MaterializedView 선택 시: 갱신 주기 또는 트리거/이벤트로 뷰 갱신. |
| **점검** | 현재 코드에는 **동기화 로직이 없음** → TO-BE 구현 시 반드시 포함. |

---

### 2.3 캐시 적용 (Redis)

| 구분 | TO-BE 내용 |
|------|------------|
| **대상 API** | 상품 상세 API, 상품 목록 API에 **Redis 캐시** 적용. |
| **설계 요소** | **TTL 설정**, **캐시 키 설계**, **무효화 전략** 중 하나 이상 반드시 포함. |
| **캐시 키 예** | 상세: `product:detail:{productId}`. 목록: `product:list:{brandId}:{sort}:{page}:{size}` 등 (파라미터 조합으로 키 생성). |
| **TTL** | 예: 상세 5분, 목록 1분 등 — 트래픽·일관성 요구에 따라 결정. |
| **무효화** | 상품 수정/삭제 시 해당 상품(및 관련 목록 키) 캐시 삭제 또는 무효화. 좋아요 등록/취소 시 해당 상품이 포함된 목록 캐시 무효화(좋아요 순 정렬 키 등). |

---

### 2.4 API별 최적화 전략 요약 (TO-BE)

| API | 인덱스 | 비정규화 / MaterializedView | Redis |
|-----|--------|-----------------------------|--------|
| **상품 목록** | 복합 인덱스 (EXPLAIN 기반) | like_count 또는 MV로 좋아요 순 정렬 | 목록 캐시 (키·TTL·무효화) |
| **상품 상세** | PK/단건 위주 | — | 상세 캐시 (키·TTL·무효화) |
| **주문 목록** | (user_id, ordered_at) 또는 (user_id, status, ordered_at) 복합 인덱스 | 목록 노출용 집계(item_count, total_amount 등) 필요 시 order 테이블 비정규화 검토 | 목록 캐시: order:list:{userId}:{start}:{end}:{page}:{size}, TTL 1~3분, 주문 생성/취소 시 무효화 |
| **주문 상세** | PK + order_item.order_id 인덱스 확인 | — | 상세 캐시: order:detail:{orderId}, TTL 5~10분, 주문 취소 시 무효화 |
| **좋아요 등록/취소** | — | 비정규화 시 like_count 갱신 | 캐시 무효화(해당 상품·목록 키) |

---

### 2.5 수정/삭제 API와 무효화

| API | TO-BE |
|-----|--------|
| **상품 수정** | 수정 처리 후 **캐시에 저장된 해당 상품(및 관련 목록) 데이터 삭제/무효화** 로직 추가. |
| **상품 삭제** | 삭제 처리 후 **관련 캐시 삭제** (상세 키 + 해당 상품이 포함될 수 있는 목록 키). |
| **주문 취소** | 주문 목록 조회 시 **CANCELLED 제외** 등 status 조건이 인덱스와 맞게 동작하는지 확인. 인덱스가 `(user_id, ordered_at)` 위주라면, status 필터가 인덱스 사용 후 적용되는지 EXPLAIN으로 검증. |

---

## 3. 성능 개선 전략 트레이드오프 (기준 정리)

- **상품/주문 조회**: 읽기 비중이 크면 인덱스·캐시·비정규화로 읽기 비용 감소; 인덱스/캐시 키가 많을수록 쓰기·메모리 부담 증가. **기준**: 목록/상세 응답 시간 목표치와 DB/Redis 리소스 한도.
- **다른 API(쓰기)**: 주문 생성·결제·쿠폰 사용 등은 이미 트랜잭션·락으로 처리. 성능 개선은 락 구간 축소, 배치 처리, 비동기 보조 작업 등으로 검토 가능. (과제 범위에서는 “가능한지 생각해보기” 수준.)
- **테이블 컬럼 값 분포**: 10만 건 준비 시 `brand_id`, `price`, `created_at` 등이 **한쪽으로 치우치지 않도록** 분포시켜, 인덱스·실행 계획이 실제 트래픽과 비슷하게 나오도록 함.

---

## 4. 체크리스트 (구현 시 검증용)

- [ ] 상품 10만 건 이상 데이터 준비 (다양한 분포).
- [ ] 상품 목록: EXPLAIN 전/후, 복합 인덱스, 성능 비교 문서화.
- [ ] 좋아요 순: like_count 또는 MaterializedView 선택 및 **등록/취소 시 count 동기화** 구현.
- [ ] 상품 상세/목록: Redis 캐시 적용 (TTL, 캐시 키, 무효화 중 1개 이상 명시).
- [ ] 상품 수정/삭제: 캐시 무효화(또는 삭제) 로직 포함.
- [ ] 주문 목록: 인덱스 정리 후, 주문 취소 시 목록 필터링·EXPLAIN 확인.

---

**문서 버전**: 1.0  
**기준 코드**: `ProductRepositoryImpl`, `ProductFacade`, `LikeService`, `OrderFacade`, `OrderRepositoryImpl`, `.docs/design/04-erd.md`



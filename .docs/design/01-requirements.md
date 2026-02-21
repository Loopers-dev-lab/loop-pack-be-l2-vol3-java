# 01. 유저 시나리오 기반 기능 정의 및 요구사항 명세

---

## 0. 문서 범위

- **구현 범위**: 전체 도메인 (회원주소, 브랜드, 상품, 좋아요, 장바구니, 재고, 주문, 결제, 포인트, 쿠폰)
- **회원(User)**: v1에서 완성. 본 문서에서는 참조만
- **핵심 변경점**:
  - OrderSheet 제거 → Order 단일 테이블로 통합 (PENDING/PAID/EXPIRED/CANCELED)
  - 재고: `inventories` 테이블 분리 + `reserved_qty` 기반 예약 (별도 예약 테이블 없음)
  - 장바구니: `carts` 테이블 제거 → `cart_items.user_id` 직접 참조
  - 좋아요: hard delete (이력 추적 불필요)
  - 가격: `base_price` 단일. 할인은 쿠폰으로만 처리
  - 쿠폰: `coupon_targets` 제거 → 주문 전체 적용. 상태 3단계 (ISSUED/USED/EXPIRED)
  - 포인트: `point_ledgers` 제거 → `point_accounts` 잔액 관리만
  - 재고 예약 만료: 30분

---

## 1. 유비쿼터스 언어

| 한글 | 영문 | 정의 | 비고 |
|------|------|------|------|
| 브랜드 | Brand | 상품을 공급하는 단위 | 삭제 시 소속 상품 연쇄 삭제 |
| 상품 | Product | 판매 단위. 하나의 Brand에 소속 | Brand와 N:1 |
| 좋아요 | Like (ProductLike/BrandLike) | 사용자가 관심 상품/브랜드를 저장 | 등록/취소 분리. hard delete |
| 장바구니 | CartItem | 구매 전 상품 임시 보관 | cart_items.user_id 직접 참조 |
| 재고 | Inventory | 상품의 판매 가능 수량 | reserved_qty 기반 예약 (quantity - reserved_qty) |
| 주문 | Order | 사용자의 구매 거래 단위 | PENDING→PAID 2단계 |
| 주문항목 | OrderItem | 주문 시점의 상품 정보 스냅샷 | Order와 1:N |
| 결제 | Payment | PG를 통한 결제 처리 | 재시도 가능 |
| 포인트 | Point | 사용자 적립금 | point_accounts 잔액 관리 |
| 쿠폰 | Coupon | 할인 수단 | 템플릿→발급→사용. 주문 전체 적용 |
| 어드민 | Admin | 운영자 | LDAP 헤더로 식별 |

### 상태 정의

| 도메인 | 상태(Enum) | 값 | 설명 |
|--------|-----------|-----|------|
| Brand | BrandStatus | `ACTIVE`, `INACTIVE` | ACTIVE만 고객 노출 |
| Product | ProductStatus | `ACTIVE`, `SOLDOUT`, `HIDDEN`, `DISCONTINUED` | ACTIVE/SOLDOUT: 고객 노출, HIDDEN/DISCONTINUED: 비노출 |
| Order | OrderStatus | `PENDING`, `PAID`, `EXPIRED`, `CANCELED` | PENDING: 결제대기, PAID: 확정, EXPIRED: 만료, CANCELED: 취소 |
| Payment | PaymentStatus | `REQUESTED`, `APPROVED`, `FAILED`, `CANCELED` | PG 결제 상태 |
| IssuedCoupon | IssuedCouponStatus | `ISSUED`, `USED`, `EXPIRED` | ISSUED: 발급, USED: 사용 확정, EXPIRED: 만료 |
| CouponTemplate | CouponTemplateStatus | `ACTIVE`, `INACTIVE`, `EXPIRED` | 템플릿 상태 |
| Like | - | 존재하면 좋아요, 없으면 해제 | hard delete (row 삭제) |

---

## 2. 공통 정책

### 2-1. 인증 체계

| 구분 | prefix | 헤더 | 설명 |
|------|--------|------|------|
| 고객 API | `/api/v1` | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | 로그인 필요 API는 헤더로 사용자 식별 |
| 어드민 API | `/api-admin/v1` | `X-Loopers-Ldap: loopers.admin` | LDAP 기반 어드민 식별 |

### 2-2. 공통 규칙

- **소프트 삭제**: 모든 삭제는 `deleted_at` 업데이트 (좋아요 제외 - hard delete)
- **페이징**: 목록 조회는 page/size 기반 (기본값: page=0, size=20)
- **정렬**: 기본 정렬은 최신순 (created_at DESC)
- **에러 응답**: 검증 실패는 예외가 아닌 Response return (CLAUDE.md 규칙)
- **재고 예약 만료**: 30분

### 2-3. 에러 응답 범주

| HTTP 상태 | 의미 | 사용 예시 |
|-----------|------|----------|
| 400 | 입력값 오류 | 필수값 누락, 형식 오류, 수량 비정상 |
| 401 | 인증 없음 | 로그인 필요 API에 헤더 없음 |
| 403 | 권한 없음 | 타인 리소스 접근, 어드민 권한 없음 |
| 404 | 리소스 없음 | 존재하지 않는 브랜드/상품/주문 |
| 409 | 상태 충돌 | 이미 좋아요함, 재고 부족, 중복 등록 |
| 500 | 서버 내부 오류 | 예상치 못한 시스템 에러 |

---

## 3. 유저 시나리오 기반 기능 정의

| 시나리오 | 대상 | 핵심 기능 | 기능 번호 |
|---------|------|----------|----------|
| A | 고객 | 탐색 + 좋아요 | F-01 ~ F-06, F-06a ~ F-06c |
| B | 고객 | 장바구니 | F-07 ~ F-10 |
| C | 고객 | 주문 + 결제 | F-11 ~ F-16 |
| D | 고객 | 쿠폰 + 포인트 | F-17 ~ F-19 |
| E | 고객 | 주소 관리 | F-21 ~ F-24 |
| F | 어드민 | 브랜드/상품 관리 | F-25 ~ F-34 |
| G | 어드민 | 주문 조회 | F-35 ~ F-36 |
| H | 어드민 | 쿠폰 관리 | F-37 ~ F-40 |

---

### 시나리오 A. "브랜드/상품 탐색 → 좋아요"

**사용자 목표**: 마음에 드는 상품/브랜드를 저장해두고 다시 쉽게 찾는다.

#### F-01. 브랜드 정보 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/brands/{brandId}` |
| **인증** | 불필요 |
| **정책** | ACTIVE 브랜드만 조회 가능. 삭제/비활성 브랜드는 404 |
| **고객 노출** | 브랜드명, 설명 (상태/관리정보 제외) |
| **예외** | 404: 브랜드 없음 또는 비활성 |

#### F-02. 상품 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/products?brandId={brandId}&sort={sort}&page={page}&size={size}` |
| **인증** | 불필요 |
| **쿼리 파라미터** | `brandId`: 브랜드 필터(선택), `sort`: latest/price_asc/likes_desc (기본: latest), `page`(기본: 0), `size`(기본: 20) |
| **정책** | ACTIVE, SOLDOUT만 노출. HIDDEN/DISCONTINUED 제외 |
| **고객 노출** | 상품명, 가격(base_price), 브랜드명, 좋아요 수, 재고 상태(품절 여부) |
| **예외** | 400: page/size 비정상 |

#### F-03. 상품 상세 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/products/{productId}` |
| **인증** | 불필요 |
| **정책** | ACTIVE/SOLDOUT만 조회 가능. 재고 상태는 "재고 있음/품절"로만 표시 |
| **예외** | 404: 상품 없음 또는 비노출 |

#### F-04. 상품 좋아요 등록

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/products/{productId}/likes` |
| **인증** | 필수 |
| **정책** | (user_id, product_id) 유일성. 이미 좋아요 시 409. like_count 동기 증가 |
| **예외** | 401: 로그인 없음, 404: 상품 없음, 409: 이미 좋아요 |

#### F-05. 상품 좋아요 취소

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api/v1/products/{productId}/likes` |
| **인증** | 필수 |
| **정책** | **hard delete** (row 삭제). like_count 동기 감소. 트랜잭션 내 보장 |
| **예외** | 401: 로그인 없음, 404: 좋아요 없음 |

#### F-06. 내가 좋아요 한 상품 목록

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/users/{userId}/likes` |
| **인증** | 필수 |
| **정책** | 본인만 조회 (타인 userId 시 403). 최근 좋아요순. 삭제된 상품 제외 |
| **예외** | 401: 로그인 없음, 403: 타인 접근 |

#### F-06a. 브랜드 좋아요 등록

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/brands/{brandId}/likes` |
| **인증** | 필수 |
| **정책** | (user_id, brand_id) 유일성. 이미 좋아요 시 409. ACTIVE 브랜드만 가능 |
| **예외** | 401: 로그인 없음, 404: 브랜드 없음/비활성, 409: 이미 좋아요 |

#### F-06b. 브랜드 좋아요 취소

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api/v1/brands/{brandId}/likes` |
| **인증** | 필수 |
| **정책** | **hard delete** (row 삭제). 트랜잭션 내 보장 |
| **예외** | 401: 로그인 없음, 404: 좋아요 없음 |

#### F-06c. 내가 좋아요 한 브랜드 목록

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/users/me/brand-likes` |
| **인증** | 필수 |
| **정책** | 본인만 조회. 최근 좋아요순. 삭제된 브랜드 제외 |
| **예외** | 401: 로그인 없음 |

---

### 시나리오 B. "장바구니 관리"

**사용자 목표**: 구매할 상품을 장바구니에 담아두고 한 번에 주문한다.

#### F-07. 장바구니 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/carts` |
| **인증** | 필수 |
| **정책** | 본인 장바구니만 조회. 상품 정보(현재 가격, 재고 상태) 실시간 반영 |

#### F-08. 장바구니 상품 추가

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/carts/items` |
| **인증** | 필수 |
| **요청** | `{ "productId": 1, "quantity": 2 }` |
| **정책** | 이미 있는 상품이면 수량 합산(merge). 상품 존재/판매가능 확인 |
| **예외** | 404: 상품 없음, 409: 판매 불가 상품 |

#### F-09. 장바구니 수량 변경

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api/v1/carts/items/{cartItemId}` |
| **인증** | 필수 |
| **요청** | `{ "quantity": 3 }` |
| **정책** | 수량 1 이상. 본인 장바구니 항목만 수정 |
| **예외** | 400: 수량 비정상, 404: 항목 없음 |

#### F-10. 장바구니 상품 삭제

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api/v1/carts/items/{cartItemId}` |
| **인증** | 필수 |
| **정책** | soft delete. 본인 장바구니 항목만 삭제 |

---

### 시나리오 C. "주문 + 결제"

**사용자 목표**: 상품을 주문하고 결제하여 구매를 완료한다.

#### F-11. 주문 생성 (핵심)

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/orders` |
| **인증** | 필수 |
| **요청** | `{ "items": [{"productId": 1, "quantity": 2}], "addressId": 5 }` |
| **정상 흐름** | 1. items 유효성 검증 2. 각 상품 존재/판매가능 확인 3. **재고 예약** (inventories.reserved_qty 증가, 비관적 락) 4. Order(PENDING) 생성 + OrderItem 스냅샷 저장 5. 배송지 스냅샷 저장 6. expires_at = now() + 30분 |
| **스냅샷 저장** | 상품명, 브랜드명, 가격(base_price), 수량, 라인합계(unit_price * quantity) |
| **재고 정책** | 주문 시점에 재고 예약을 **원자적으로** 수행. 부분 성공 없음 (전체 성공 or 전체 실패) |
| **예외** | 400: items 비어있음/수량 비정상, 404: 상품/주소 없음, 409: 재고 부족/판매 불가 |

**설계 의도 - 재고 예약**:
- `inventories.reserved_qty`를 증가시켜 재고를 홀드 (비관적 락)
- 실제 `quantity` 차감은 결제 성공 시
- 30분 내 미결제 시 배치가 Order(PENDING) + expires_at 기준으로 만료 처리 (reserved_qty 복구)

#### F-12. 할인 적용 (쿠폰/포인트)

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api/v1/orders/{orderId}/discount` |
| **인증** | 필수 |
| **요청** | `{ "couponCodes": ["COUPON-A"], "pointAmount": 1000 }` |
| **정책** | PENDING 상태에서만 가능. 쿠폰 적용 가능 여부 검증 (ISSUED 상태 확인). 포인트는 잔액 검증만 |
| **금액 재계산** | total_amount = subtotal - discount - point + shipping |
| **예외** | 400: 쿠폰 적용 불가, 409: 포인트 잔액 부족, 404: 주문 없음 |

#### F-13. 결제 요청 (핵심)

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/orders/{orderId}/pay` |
| **인증** | 필수 |
| **요청** | `{ "paymentMethod": "CARD" }` |
| **정상 흐름** | 1. Order PENDING 확인 2. Payment(REQUESTED) 생성 3. PG 결제 승인 요청 |
| **결제 성공 시** | Payment→APPROVED, 재고 확정 (quantity 차감 + reserved_qty 감소), Point 차감, Coupon→USED, Order→PAID |
| **결제 실패 시** | Payment→FAILED, reserved_qty 복구 (order_items 기준) |
| **예외** | 404: 주문 없음, 409: 이미 결제됨/만료됨 |

#### F-14. 유저 주문 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/orders?startAt={startAt}&endAt={endAt}` |
| **인증** | 필수 |
| **정책** | 본인 주문만. 날짜 범위 선택(미지정 시 최근 3개월). 최신순 |
| **예외** | 400: 날짜 형식 오류/startAt > endAt |

#### F-15. 단일 주문 상세 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/orders/{orderId}` |
| **인증** | 필수 |
| **정책** | 본인 주문만. 스냅샷 기반 (상품 변경/삭제 무관) |
| **예외** | 401: 로그인 없음, 403: 타인 주문, 404: 주문 없음 |

#### F-16. 주문 취소

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api/v1/orders/{orderId}` |
| **인증** | 필수 |
| **정책** | PENDING 상태에서만 취소 가능. reserved_qty 복구 (order_items 기준), Order→CANCELED |
| **예외** | 409: 이미 결제됨/만료됨 |

---

### 시나리오 D. "쿠폰 + 포인트"

**사용자 목표**: 보유한 쿠폰과 포인트로 할인받아 결제한다.

#### F-17. 쿠폰 발급

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/coupons/issue` |
| **인증** | 필수 |
| **요청** | `{ "couponTemplateId": 1 }` |
| **정책** | 발급 가능 여부 확인 (유효기간, 발급제한, 유저별 제한). 고유 코드 자동 생성. status=ISSUED |
| **예외** | 409: 발급 제한 초과, 404: 템플릿 없음 |

#### F-18. 내 쿠폰 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/users/me/coupons` |
| **인증** | 필수 |
| **정책** | 본인 쿠폰만. 상태별 필터 가능 |

#### F-19. 포인트 잔액 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/users/me/points` |
| **인증** | 필수 |

---

### 시나리오 E. "주소 관리"

#### F-21. 주소 목록 조회

| 항목 | 내용 |
|------|------|
| **API** | `GET /api/v1/users/me/addresses` |
| **인증** | 필수 |

#### F-22. 주소 등록

| 항목 | 내용 |
|------|------|
| **API** | `POST /api/v1/users/me/addresses` |
| **인증** | 필수 |
| **정책** | 첫 번째 주소는 자동으로 기본주소 설정 |

#### F-23. 주소 수정

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api/v1/users/me/addresses/{addressId}` |
| **인증** | 필수 |

#### F-24. 주소 삭제

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api/v1/users/me/addresses/{addressId}` |
| **인증** | 필수 |
| **정책** | soft delete. 기본주소 삭제 시 다른 주소를 기본으로 전환 |

---

### 시나리오 F. "어드민 - 브랜드/상품 관리"

#### F-25. 브랜드 목록 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/brands?page=0&size=20` |
| **인증** | LDAP 필수 |
| **정책** | 삭제된 브랜드 제외. 모든 상태 조회 가능 |
| **어드민 노출** | 고객 노출 정보 + 상태, 등록일, 수정일 |

#### F-26. 브랜드 상세 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/brands/{brandId}` |
| **인증** | LDAP 필수 |

#### F-27. 브랜드 등록 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `POST /api-admin/v1/brands` |
| **인증** | LDAP 필수 |
| **정책** | 브랜드명 필수. 초기 상태 ACTIVE |

#### F-28. 브랜드 수정 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api-admin/v1/brands/{brandId}` |
| **인증** | LDAP 필수 |

#### F-29. 브랜드 삭제 (Admin) - 연쇄 삭제

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api-admin/v1/brands/{brandId}` |
| **인증** | LDAP 필수 |
| **핵심 정책** | **브랜드 삭제 시 소속 상품 + 재고 일괄 soft delete**. 기존 주문 스냅샷 영향 없음 |
| **예외** | 404: 없음, 409: 이미 삭제 |

#### F-30. 상품 목록 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/products?page=0&size=20&brandId={brandId}` |
| **인증** | LDAP 필수 |
| **어드민 노출** | 모든 필드 (재고 수량 포함) |

#### F-31. 상품 상세 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/products/{productId}` |
| **인증** | LDAP 필수 |

#### F-32. 상품 등록 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `POST /api-admin/v1/products` |
| **인증** | LDAP 필수 |
| **핵심 정책** | 브랜드가 ACTIVE여야 등록 가능. 상품 등록 시 inventories 레코드도 함께 생성 |
| **예외** | 404: 브랜드 없음, 409: 삭제된 브랜드 |

#### F-33. 상품 수정 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api-admin/v1/products/{productId}` |
| **인증** | LDAP 필수 |
| **핵심 정책** | **brand_id 수정 불가** |

#### F-34. 상품 삭제 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api-admin/v1/products/{productId}` |
| **인증** | LDAP 필수 |
| **정책** | 상품 + 재고 soft delete. 기존 주문 스냅샷 영향 없음 |

---

### 시나리오 G. "어드민 - 주문 조회"

#### F-35. 주문 목록 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/orders?page=0&size=20` |
| **인증** | LDAP 필수 |

#### F-36. 주문 상세 조회 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/orders/{orderId}` |
| **인증** | LDAP 필수 |

---

### 시나리오 H. "어드민 - 쿠폰 관리"

#### F-37. 쿠폰 템플릿 목록 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `GET /api-admin/v1/coupon-templates?page=0&size=20` |
| **인증** | LDAP 필수 |

#### F-38. 쿠폰 템플릿 생성 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `POST /api-admin/v1/coupon-templates` |
| **인증** | LDAP 필수 |
| **정책** | 할인유형(FIXED/PERCENT), 할인값, 최소주문금액, 유효기간 등 설정. 쿠폰은 주문 전체에 적용 |

#### F-39. 쿠폰 템플릿 수정 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `PUT /api-admin/v1/coupon-templates/{templateId}` |
| **인증** | LDAP 필수 |

#### F-40. 쿠폰 템플릿 삭제 (Admin)

| 항목 | 내용 |
|------|------|
| **API** | `DELETE /api-admin/v1/coupon-templates/{templateId}` |
| **인증** | LDAP 필수 |
| **정책** | soft delete. 이미 발급된 쿠폰에는 영향 없음 |

---

## 4. 고객 / 어드민 정보 분리

### 브랜드 정보

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| id | O | O | |
| name | O | O | |
| description | O | O | |
| status | X | O | 고객에게는 ACTIVE만 보임 |
| created_at | X | O | |
| updated_at | X | O | |

### 상품 정보

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| id | O | O | |
| name | O | O | |
| base_price | O | O | |
| description | O | O | |
| brand_name | O | O | |
| status | 간접(배지) | O | 고객: "품절" 배지로만 |
| 재고 수량 | X | O | 고객: "재고 있음/품절" |
| like_count | O | O | |
| created_at | X | O | |
| updated_at | X | O | |

### 주문 정보

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| id | O | O | |
| order_number | O | O | |
| status | O | O | |
| orderer_name | O | O | |
| orderer_phone | O | O | |
| receiver_name/phone | O | O | 배송지 스냅샷 |
| zip_code/address | O | O | |
| subtotal_amount | O | O | |
| discount_amount | O | O | |
| point_used_amount | O | O | |
| shipping_fee | O | O | |
| total_amount | O | O | |
| items (OrderItem) | O | O | 스냅샷 |
| user_id | X | O | 어드민은 주문자 식별 가능 |
| payment_id | X | O | 내부 관리 |
| expires_at | X | O | 내부 관리 |
| canceled_at | X | O | |
| created_at | X | O | |
| updated_at | X | O | |

### 주문항목 정보 (OrderItem — 스냅샷)

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| product_name | O | O | |
| brand_name | O | O | |
| unit_price | O | O | |
| quantity | O | O | |
| line_total | O | O | |
| product_id | X | O | 어드민만 원본 상품 추적 |

### 결제 정보

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| payment_method | O | O | "카드" 등 |
| status | 간접 | O | 고객은 주문 status로 충분 |
| approved_amount | O | O | 결제 금액 |
| pg_txn_id | X | O | PG 내부 식별자 |
| idempotency_key | X | O | 내부 관리 |
| requested_at | X | O | |
| approved_at | X | O | |
| failed_at | X | O | |

### 쿠폰 정보 (IssuedCoupon — 고객용)

| 필드 | 고객 | 비고 |
|------|------|------|
| code | O | |
| status | O | ISSUED/USED/EXPIRED |
| 쿠폰명 (template.name) | O | 조인 |
| 할인 정보 (type/value) | O | 조인 |
| issued_at | O | |
| used_order_id | X | 내부 관리 |

### 쿠폰 템플릿 정보 (CouponTemplate — 어드민 전용)

| 필드 | 고객 | 어드민 | 비고 |
|------|------|--------|------|
| 전체 필드 | X | O | 고객은 직접 접근 안 함 |

### 고객 전용 도메인 (어드민 API 없음)

| 도메인 | 고객 노출 필드 | 비고 |
|--------|-------------|------|
| 장바구니 (CartItem) | quantity + 상품 실시간 정보 (name, price, 품절 여부) | 상품 정보는 조인 |
| 상품 좋아요 (ProductLike) | productId, createdAt + 상품 기본 정보 | 삭제된 상품 제외 |
| 브랜드 좋아요 (BrandLike) | brandId, createdAt + 브랜드 기본 정보 | 삭제된 브랜드 제외 |
| 배송지 (UserAddress) | 전체 필드 | 본인 데이터 |
| 포인트 (PointAccount) | balance | 잔액만 |
| 재고 (Inventory) | X (직접 노출 안 함) | Product에서 "재고 있음/품절"로만 표현 |

---

## 5. 설계 결정 포인트

| # | 항목 | 결정 | 근거 |
|---|------|------|------|
| 1 | OrderSheet 통합 | Order 단일 테이블 + PENDING 상태 | 데이터 중복 제거. 상태 확장으로 충분 |
| 2 | Like 삭제 방식 | hard delete | 이력 불필요. UK 충돌 문제 해소 |
| 3 | 가격 필드 | base_price 단일 | 할인은 쿠폰으로만 처리 |
| 4 | 재고 관리 | inventories 분리 + reserved_qty 기반 예약 | Order(PENDING)이 예약 역할. 별도 예약 테이블 없음 |
| 5 | 재고 예약 만료 | 30분 | 충분한 결제 시간 + 재고 점유 최소화 |
| 6 | 만료 처리 방식 | Batch Scheduler | commerce-batch 모듈 활용 |
| 7 | Like count | products.like_count 비정규화 | 조회 성능. 동기 증감 |
| 8 | 브랜드 삭제 | 연쇄 soft delete (상품 + 재고) | 요구사항 명시 |
| 9 | 쿠폰 추적 | issued_coupons.used_order_id | 결제 성공 시 USED + 주문 ID 기록 |
| 10 | 결제 재시도 | orders(1) : payments(N) | 하나의 주문에 여러 결제 시도 가능 |
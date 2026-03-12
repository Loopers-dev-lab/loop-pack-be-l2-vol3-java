# 커머스 도메인 요구사항 정의서

## 1. 개요

### 1.1 문서 목적
Java/Spring Boot 멀티 모듈 커머스 백엔드의 Brand, Product, ProductLike, Order, OrderItem 도메인에 대한
상세 요구사항을 정의한다.

### 1.2 기존 패턴 참조
- 아키텍처: Layered Architecture (interfaces → application → domain → infrastructure)
- 인증: 헤더 기반 인증 (X-Loopers-LoginId, X-Loopers-LoginPw)
- 응답 형식: ApiResponse<T> (meta + data)
- 예외 처리: CoreException + ErrorType enum

### 1.3 액터 정의

| 액터 | 설명 | 인증 방식 |
|------|------|----------|
| 일반 사용자 | 상품 조회, 좋아요, 주문 가능 | X-Loopers-LoginId + X-Loopers-LoginPw |
| 어드민 | 브랜드/상품 CRUD 관리 | X-Loopers-Ldap: loopers.admin |

---

## 2. 도메인별 상세 요구사항

### 2.1 Brand (브랜드)

#### 2.1.1 필드 정의
| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| id | Long | Y | 자동 생성 (PK) |
| name | String | Y | 1-100자, 공백 불가, 중복 불가 |
| description | String | N | 최대 500자 |
| logoUrl | String | N | URL 형식 검증, 최대 500자 |
| createdAt | ZonedDateTime | Y | 자동 생성 |
| updatedAt | ZonedDateTime | Y | 자동 갱신 |
| deletedAt | ZonedDateTime | N | Soft Delete |

*브랜드 주소, 대표명, 브랜드 사이트 URL 같은 컬럼을 넣을지 말지 고민했지만 설계에 집중하고 싶어서 넣지 않았다.*

#### 2.1.2 비즈니스 규칙
- **BR-BRAND-001**: 브랜드명은 시스템 내 유일해야 한다
- **BR-BRAND-002**: 브랜드 삭제 시 해당 브랜드의 모든 상품이 Cascade 삭제된다
- **BR-BRAND-003**: 삭제된 브랜드는 조회되지 않는다 (Soft Delete)

#### 2.1.3 검증 규칙
```
name 검증:
- null 또는 blank 불가 → "브랜드명은 필수입니다."
- 100자 초과 → "브랜드명은 100자를 초과할 수 없습니다."
- 중복 → "이미 존재하는 브랜드명입니다." (409 CONFLICT)

description 검증:
- 500자 초과 → "브랜드 설명은 500자를 초과할 수 없습니다."

logoUrl 검증:
- URL 형식 불일치 → "유효하지 않은 URL 형식입니다."
- 500자 초과 → "로고 URL은 500자를 초과할 수 없습니다."
```

---

### 2.2 Product (상품)

#### 2.2.1 필드 정의
| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| id | Long | Y | 자동 생성 (PK) |
| brandId | Long | Y | Brand FK, 존재 검증 |
| name | String | Y | 1-200자 |
| description | String | N | 최대 2000자 |
| price | Long | Y | 0 이상 |
| stock | Integer | Y | 0 이상 |
| imageUrl | String | N | URL 형식, 최대 500자 |
| createdAt | ZonedDateTime | Y | 자동 생성 |
| updatedAt | ZonedDateTime | Y | 자동 갱신 |
| deletedAt | ZonedDateTime | N | Soft Delete |

#### 2.2.2 비즈니스 규칙
- **BR-PRODUCT-001**: 상품은 반드시 하나의 브랜드에 속해야 한다
- **BR-PRODUCT-002**: 상품 등록 후 브랜드 변경 불가
- **BR-PRODUCT-003**: 상품 삭제 시 해당 상품의 모든 좋아요가 Cascade 삭제된다
- **BR-PRODUCT-004**: 삭제된 상품은 목록 조회 시 제외된다
- **BR-PRODUCT-005**: 재고가 0인 상품도 조회는 가능하다

#### 2.2.3 검증 규칙
```
name 검증:
- null 또는 blank 불가 → "상품명은 필수입니다."
- 200자 초과 → "상품명은 200자를 초과할 수 없습니다."

price 검증:
- null 불가 → "가격은 필수입니다."
- 음수 → "가격은 0원 이상이어야 합니다."

stock 검증:
- null 불가 → "재고는 필수입니다."
- 음수 → "재고는 0개 이상이어야 합니다."

brandId 검증:
- 존재하지 않는 브랜드 → "존재하지 않는 브랜드입니다." (404 NOT_FOUND)
- 삭제된 브랜드 → "삭제된 브랜드입니다." (400 BAD_REQUEST)
```

---

### 2.3 ProductLike (상품 좋아요)

#### 2.3.1 필드 정의
| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| id | Long | Y | 자동 생성 (PK) |
| userId | Long | Y | User FK |
| productId | Long | Y | Product FK |
| createdAt | ZonedDateTime | Y | 자동 생성 |

#### 2.3.2 비즈니스 규칙
- **BR-LIKE-001**: 좋아요 등록 시 이미 좋아요가 존재하면 좋아요 취소 처리 (토글 방식)
- **BR-LIKE-002**: 좋아요 개수는 실시간 COUNT 집계
- **BR-LIKE-003**: 존재하지 않는 좋아요 취소 시 멱등 처리 (에러 없이 성공 응답)
- **BR-LIKE-004**: 삭제된 상품에는 좋아요 불가

#### 2.3.3 검증 규칙
```
좋아요 등록:
- 삭제된 상품 → "삭제된 상품입니다." (400 BAD_REQUEST)
- 존재하지 않는 상품 → "존재하지 않는 상품입니다." (404 NOT_FOUND)
- 중복 좋아요 → 좋아요 취소 처리 (토글)

좋아요 취소:
- 존재하지 않는 좋아요 → 멱등 처리 (성공 응답)
```

---

### 2.4 Order (주문)

#### 2.4.1 필드 정의
| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| id | Long | Y | 자동 생성 (PK) |
| userId | Long | Y | User FK |
| totalPrice | Long | Y | 0 이상, 계산된 값 |
| status | OrderStatus | Y | PENDING, COMPLETED, CANCELLED |
| createdAt | ZonedDateTime | Y | 자동 생성 |
| updatedAt | ZonedDateTime | Y | 자동 갱신 |

#### 2.4.2 OrderStatus 상태 정의
```java
public enum OrderStatus {
    PENDING,    // 주문 대기
    COMPLETED,  // 주문 완료
    CANCELLED   // 주문 취소
}
```

#### 2.4.3 비즈니스 규칙
- **BR-ORDER-001**: 다건 상품 주문 지원 (OrderItem 1:N 관계)
- **BR-ORDER-002**: 전체 실패 정책 - 하나라도 실패 시 전체 주문 롤백
- **BR-ORDER-003**: 재고 검증 후 차감은 원자적으로 수행 (비관적 락)
- **BR-ORDER-004**: totalPrice는 OrderItem들의 (price * quantity) 합계

---

### 2.5 OrderItem (주문 항목)

#### 2.5.1 필드 정의
| 필드 | 타입 | 필수 | 제약조건 |
|------|------|------|----------|
| id | Long | Y | 자동 생성 (PK) |
| orderId | Long | Y | Order FK |
| productId | Long | Y | Product FK |
| quantity | Integer | Y | 1 이상 |
| price | Long | Y | 주문 시점 상품 가격 (스냅샷) |
| createdAt | ZonedDateTime | Y | 자동 생성 |

#### 2.5.2 비즈니스 규칙
- **BR-ORDERITEM-001**: 주문 시점의 상품 가격을 스냅샷으로 저장
- **BR-ORDERITEM-002**: 동일 주문 내 동일 상품 중복 불가 (orderId + productId UNIQUE)
- **BR-ORDERITEM-003**: 수량은 최소 1개 이상

---

## 3. 유저 시나리오

### 3.1 일반 사용자 시나리오

#### US-001: 브랜드 정보 조회
```
Given: 사용자가 로그인 상태
When: GET /api/v1/brands/{brandId} 요청
Then: 브랜드 정보(name, description, logoUrl) 반환
```

#### US-002: 상품 목록 조회
```
Given: 사용자가 로그인 상태
When: GET /api/v1/products 요청 (정렬/필터/페이징 옵션)
Then: 상품 목록과 좋아요 수 반환

정렬 옵션:
- latest (기본값): 최신순
- price_asc: 가격 낮은순
- like_desc: 좋아요 많은순

필터 옵션:
- brandId: 특정 브랜드 필터

페이징:
- page: 페이지 번호 (0부터 시작)
- size: 페이지 크기 (기본 20)
```

#### US-003: 상품 상세 조회
```
Given: 사용자가 로그인 상태
When: GET /api/v1/products/{productId} 요청
Then: 상품 상세 정보와 좋아요 수 반환
```

#### US-004: 좋아요 등록/토글
```
Given: 사용자가 로그인 상태
When: POST /api/v1/products/{productId}/likes 요청
Then:
  - 좋아요가 없으면 → 좋아요 등록
  - 좋아요가 있으면 → 좋아요 취소 (토글)
```

#### US-005: 좋아요 취소
```
Given: 사용자가 로그인 상태
When: DELETE /api/v1/products/{productId}/likes 요청
Then: 좋아요 삭제 (존재하지 않아도 성공)
```

#### US-006: 내가 좋아요한 상품 목록
```
Given: 사용자가 로그인 상태
When: GET /api/v1/users/{userId}/likes 요청
Then: 좋아요한 상품 목록 반환
```

#### US-007: 주문 생성
```
Given: 사용자가 로그인 상태, 주문할 상품들 선택
When: POST /api/v1/orders 요청 (items: [{productId, quantity}])
Then:
  - 재고 검증 (모든 상품)
  - 재고 차감 (원자적)
  - 주문 생성 및 ID 반환
  - 실패 시 전체 롤백
```

#### US-008: 주문 목록 조회
```
Given: 사용자가 로그인 상태
When: GET /api/v1/orders 요청 (선택적 기간 필터)
Then: 본인의 주문 목록 반환
```

#### US-009: 주문 상세 조회
```
Given: 사용자가 로그인 상태
When: GET /api/v1/orders/{orderId} 요청
Then:
  - 본인 주문인 경우: 주문 상세 반환
  - 타인 주문인 경우: 403 FORBIDDEN
```

---

### 3.2 어드민 시나리오

#### AS-001: 브랜드 목록 조회 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: GET /api-admin/v1/brands 요청
Then: 전체 브랜드 목록 반환
```

#### AS-002: 브랜드 등록 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: POST /api-admin/v1/brands 요청
Then: 브랜드 등록 및 ID 반환
```

#### AS-003: 브랜드 수정 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: PUT /api-admin/v1/brands/{brandId} 요청
Then: 브랜드 정보 수정
```

#### AS-004: 브랜드 삭제 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: DELETE /api-admin/v1/brands/{brandId} 요청
Then:
  - 브랜드 Soft Delete
  - 해당 브랜드의 모든 상품 Cascade Soft Delete
  - 상품들의 좋아요 Cascade Hard Delete
```

#### AS-005: 상품 등록 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: POST /api-admin/v1/products 요청
Then: 상품 등록 및 ID 반환
```

#### AS-006: 상품 수정 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: PUT /api-admin/v1/products/{productId} 요청
Then:
  - brandId 제외 필드 수정 가능
  - brandId 변경 시도 시: "브랜드 변경은 불가능합니다." (400 BAD_REQUEST)
```

#### AS-007: 상품 삭제 (Admin)
```
Given: X-Loopers-Ldap: loopers.admin 헤더
When: DELETE /api-admin/v1/products/{productId} 요청
Then:
  - 상품 Soft Delete
  - 좋아요 Cascade Hard Delete
```

---

## 4. API 명세

### 4.1 일반 사용자 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/v1/brands/{brandId} | 브랜드 정보 조회 |
| GET | /api/v1/products | 상품 목록 조회 |
| GET | /api/v1/products/{productId} | 상품 상세 조회 |
| POST | /api/v1/products/{productId}/likes | 좋아요 등록 |
| DELETE | /api/v1/products/{productId}/likes | 좋아요 취소 |
| GET | /api/v1/users/{userId}/likes | 내가 좋아요한 상품 목록 |
| POST | /api/v1/orders | 주문 생성 |
| GET | /api/v1/orders | 주문 목록 조회 |
| GET | /api/v1/orders/{orderId} | 주문 상세 조회 |

### 4.2 어드민 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api-admin/v1/brands | 브랜드 목록 조회 |
| GET | /api-admin/v1/brands/{brandId} | 브랜드 상세 조회 |
| POST | /api-admin/v1/brands | 브랜드 등록 |
| PUT | /api-admin/v1/brands/{brandId} | 브랜드 수정 |
| DELETE | /api-admin/v1/brands/{brandId} | 브랜드 삭제 |
| GET | /api-admin/v1/products | 상품 목록 조회 |
| GET | /api-admin/v1/products/{productId} | 상품 상세 조회 |
| POST | /api-admin/v1/products | 상품 등록 |
| PUT | /api-admin/v1/products/{productId} | 상품 수정 |
| DELETE | /api-admin/v1/products/{productId} | 상품 삭제 |

---

## 5. 인증 체계

### 5.1 일반 사용자 인증
```
Headers:
  X-Loopers-LoginId: {loginId}
  X-Loopers-LoginPw: {password}

처리: AuthenticatedUserArgumentResolver
대상: /api/v1/** 엔드포인트
```

### 5.2 어드민 인증
```
Headers:
  X-Loopers-Ldap: loopers.admin

처리: AdminAuthInterceptor + AdminUserArgumentResolver
대상: /api-admin/v1/** 엔드포인트
```

---

## 6. 에러 타입 정의

```java
// ErrorType.java에 추가할 타입
BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "존재하지 않는 브랜드입니다."),
BRAND_ALREADY_EXISTS(HttpStatus.CONFLICT, "BRAND_ALREADY_EXISTS", "이미 존재하는 브랜드명입니다."),
BRAND_DELETED(HttpStatus.BAD_REQUEST, "BRAND_DELETED", "삭제된 브랜드입니다."),
PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."),
PRODUCT_DELETED(HttpStatus.BAD_REQUEST, "PRODUCT_DELETED", "삭제된 상품입니다."),
INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "존재하지 않는 주문입니다."),
ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "주문 접근 권한이 없습니다."),
ADMIN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "관리자 권한이 필요합니다."),
BRAND_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "BRAND_CHANGE_NOT_ALLOWED", "브랜드 변경은 불가능합니다.");
```

---

## 7. API 응답 형식

### 7.1 성공 응답
```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": { ... }
}
```

### 7.2 실패 응답
```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "PRODUCT_NOT_FOUND",
    "message": "존재하지 않는 상품입니다."
  },
  "data": null
}
```
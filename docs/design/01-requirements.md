# 01. 요구사항 정의서

> 이커머스 도메인(Brand, Product, ProductLike, Order, OrderItem) 설계를 위한 요구사항 정의
> base branch: main

---

## 목차

1. [개요](#1-개요)
   - 1.1 목적
   - 1.2 액터
   - 1.3 도메인 개념 모델
   - 1.4 유비쿼터스 언어
2. [Brand 도메인](#2-brand-도메인)
   - 2.1 API 목록
   - 2.2 필드 정의
   - 2.3 유스케이스
   - 2.4 에러 케이스
3. [Product 도메인](#3-product-도메인)
   - 3.1 API 목록
   - 3.2 필드 정의
   - 3.3 유스케이스
   - 3.4 에러 케이스
4. [ProductLike 도메인](#4-productlike-도메인)
   - 4.1 API 목록
   - 4.2 필드 정의
   - 4.3 유스케이스
   - 4.4 에러 케이스
5. [Order / OrderItem 도메인](#5-order--orderitem-도메인)
   - 5.1 API 목록
   - 5.2 필드 정의
   - 5.3 유스케이스
   - 5.4 에러 케이스
6. [공통 정책](#6-공통-정책)
7. [설계 결정 사항](#7-설계-결정-사항)

---

## 1. 개요

### 1.1 목적

사용자가 상품을 조회하고, 좋아요를 누르고, 주문할 수 있는 이커머스 플랫폼의 핵심 도메인을 정의한다.
1주차에 구현된 User 도메인(회원가입, 인증)을 기반으로, 상품/주문 흐름을 설계한다.

### 1.2 액터

| 액터 | 설명 | 인증 |
|------|------|------|
| 비회원 | 상품/브랜드 조회만 가능 | 불필요 |
| 회원 (User) | 좋아요, 주문 등 모든 기능 사용 | `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더 |

### 1.3 도메인 개념 모델

```
User (1주차 완료, 참조만)
 ├── 1:N → Order (회원이 주문을 생성한다)
 └── 1:N → ProductLike (회원이 상품에 좋아요를 누른다)

Brand
 └── 1:N → Product (브랜드가 여러 상품을 가진다)

Product
 ├── 1:N → ProductLike (상품에 여러 좋아요가 달린다)
 └── 1:N → OrderItem (상품이 여러 주문 항목에 포함된다)

Order
 └── 1:N → OrderItem (주문은 여러 주문 항목을 가진다)
```

### 1.4 유비쿼터스 언어

| 용어 | 의미 | 비고 |
|------|------|------|
| Brand | 상품을 공급하는 브랜드 | |
| Product | 판매 상품 | Brand에 소속 |
| ProductLike | 회원의 상품 좋아요 | User-Product 간 관계 |
| Order | 주문 | 회원이 생성 |
| OrderItem | 주문 내 개별 상품 항목 | Order-Product 간 관계 |
| User | 회원 | 1주차 구현 완료, FK 참조 |

---

## 2. Brand 도메인

### 2.1 API 목록

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/brands` | 브랜드 등록 | 필요 |
| GET | `/api/v1/brands/{brandId}` | 브랜드 단건 조회 | 불필요 |
| GET | `/api/v1/brands` | 브랜드 목록 조회 | 불필요 |

### 2.2 필드 정의

| 필드명 | 타입 | 제약조건 | 검증 규칙 |
|--------|------|----------|-----------|
| id | Long | PK, AUTO_INCREMENT | BaseEntity |
| name | String | NOT NULL, UNIQUE | 1~50자, 공백 불가 |
| createdAt | ZonedDateTime | NOT NULL | BaseEntity |
| updatedAt | ZonedDateTime | NOT NULL | BaseEntity |
| deletedAt | ZonedDateTime | nullable | BaseEntity (Soft Delete) |

### 2.3 유스케이스

#### UC-B01: 브랜드 등록

**Main Flow**
1. 회원이 브랜드 이름을 입력하여 등록을 요청한다.
2. 시스템은 브랜드 이름의 형식을 검증한다.
3. 시스템은 동일한 이름의 브랜드가 존재하는지 확인한다.
4. 브랜드를 저장하고 생성된 정보를 응답한다.

**Alternate Flow**
- 없음

**Exception Flow**
- E1: 브랜드 이름이 비어 있거나 50자 초과 → `400 Bad Request`
- E2: 동일한 이름의 브랜드가 이미 존재 → `409 Conflict`
- E3: 인증 실패 (헤더 누락 또는 불일치) → `401 Unauthorized`

#### UC-B02: 브랜드 단건 조회

**Main Flow**
1. 사용자가 brandId로 브랜드 정보를 조회한다.
2. 시스템은 해당 브랜드를 반환한다.

**Exception Flow**
- E1: 해당 brandId의 브랜드가 존재하지 않음 → `404 Not Found`

#### UC-B03: 브랜드 목록 조회

**Main Flow**
1. 사용자가 브랜드 목록을 조회한다.
2. 시스템은 전체 브랜드 목록을 반환한다.

**Alternate Flow**
- A1: 등록된 브랜드가 없으면 빈 리스트를 반환한다.

### 2.4 에러 케이스

| 상황 | HTTP Status | errorCode | message |
|------|-------------|-----------|---------|
| 이름 누락/형식 오류 | 400 | Bad Request | 브랜드 이름은 1~50자여야 합니다 |
| 이름 중복 | 409 | Conflict | 이미 등록된 브랜드 이름입니다 |
| 브랜드 미존재 | 404 | Not Found | 해당 브랜드를 찾을 수 없습니다 |
| 인증 실패 | 401 | Unauthorized | 인증 정보가 유효하지 않습니다 |

---

## 3. Product 도메인

### 3.1 API 목록

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/products` | 상품 등록 | 필요 |
| GET | `/api/v1/products/{productId}` | 상품 단건 조회 | 불필요 |
| GET | `/api/v1/products` | 상품 목록 조회 | 불필요 |

### 3.2 필드 정의

| 필드명 | 타입 | 제약조건 | 검증 규칙 |
|--------|------|----------|-----------|
| id | Long | PK, AUTO_INCREMENT | BaseEntity |
| brandId | Long | FK(brand.id), NOT NULL | 존재하는 Brand 참조 |
| name | String | NOT NULL | 1~100자 |
| price | Long | NOT NULL | 0 이상 |
| description | String | nullable | 최대 500자 |
| stockQuantity | Integer | NOT NULL | 0 이상 |
| createdAt | ZonedDateTime | NOT NULL | BaseEntity |
| updatedAt | ZonedDateTime | NOT NULL | BaseEntity |
| deletedAt | ZonedDateTime | nullable | BaseEntity (Soft Delete) |

### 3.3 유스케이스

#### UC-P01: 상품 등록

**Main Flow**
1. 회원이 브랜드 ID, 상품명, 가격, 설명, 재고 수량을 입력하여 등록을 요청한다.
2. 시스템은 필드 형식을 검증한다.
3. 시스템은 brandId에 해당하는 브랜드가 존재하는지 확인한다.
4. 상품을 저장하고 생성된 정보를 응답한다.

**Exception Flow**
- E1: 필수 필드 누락 또는 형식 오류 → `400 Bad Request`
- E2: 가격이 음수 → `400 Bad Request`
- E3: 존재하지 않는 brandId → `404 Not Found`
- E4: 인증 실패 → `401 Unauthorized`

#### UC-P02: 상품 단건 조회

**Main Flow**
1. 사용자가 productId로 상품 정보를 조회한다.
2. 시스템은 상품 정보 + 소속 브랜드 이름을 함께 응답한다.

**Exception Flow**
- E1: 해당 productId의 상품이 존재하지 않음 → `404 Not Found`

#### UC-P03: 상품 목록 조회

**Main Flow**
1. 사용자가 상품 목록을 조회한다.
2. 시스템은 상품 목록을 반환한다.

**Alternate Flow**
- A1: 등록된 상품이 없으면 빈 리스트를 반환한다.

### 3.4 에러 케이스

| 상황 | HTTP Status | errorCode | message |
|------|-------------|-----------|---------|
| 필수 필드 누락 | 400 | Bad Request | 필수 입력값입니다 |
| 가격 음수 | 400 | Bad Request | 가격은 0 이상이어야 합니다 |
| 재고 음수 | 400 | Bad Request | 재고 수량은 0 이상이어야 합니다 |
| 브랜드 미존재 | 404 | Not Found | 해당 브랜드를 찾을 수 없습니다 |
| 상품 미존재 | 404 | Not Found | 해당 상품을 찾을 수 없습니다 |
| 인증 실패 | 401 | Unauthorized | 인증 정보가 유효하지 않습니다 |

---

## 4. ProductLike 도메인

### 4.1 API 목록

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/products/{productId}/likes` | 상품 좋아요 | 필요 |
| DELETE | `/api/v1/products/{productId}/likes` | 상품 좋아요 취소 | 필요 |

### 4.2 필드 정의

| 필드명 | 타입 | 제약조건 | 검증 규칙 |
|--------|------|----------|-----------|
| id | Long | PK, AUTO_INCREMENT | BaseEntity |
| userId | Long | FK(user.id), NOT NULL | 존재하는 User 참조 |
| productId | Long | FK(product.id), NOT NULL | 존재하는 Product 참조 |
| createdAt | ZonedDateTime | NOT NULL | BaseEntity |
| updatedAt | ZonedDateTime | NOT NULL | BaseEntity |

> **참고**: Hard Delete 정책(Q-L01) 적용으로 `deletedAt` 컬럼은 사용하지 않는다. BaseEntity를 상속하므로 JPA 엔티티에는 필드가 존재하나, 비즈니스적으로 미사용.

**UNIQUE 제약**: (userId, productId) 복합 유니크 — 동일 사용자가 같은 상품에 중복 좋아요 불가

### 4.3 유스케이스

#### UC-L01: 상품 좋아요

**Main Flow**
1. 회원이 특정 상품에 좋아요를 요청한다.
2. 시스템은 해당 상품이 존재하는지 확인한다.
3. 시스템은 이미 좋아요한 상태인지 확인한다.
4. 좋아요를 저장하고 결과를 응답한다.

**Exception Flow**
- E1: 상품이 존재하지 않음 → `404 Not Found`
- E2: 이미 좋아요한 상품 → `409 Conflict`
- E3: 인증 실패 → `401 Unauthorized`

#### UC-L02: 상품 좋아요 취소

**Main Flow**
1. 회원이 특정 상품의 좋아요 취소를 요청한다.
2. 시스템은 해당 좋아요 기록이 존재하는지 확인한다.
3. 좋아요를 물리 삭제(Hard Delete)하고 결과를 응답한다.

**Exception Flow**
- E1: 좋아요 기록이 존재하지 않음 → `404 Not Found`
- E2: 인증 실패 → `401 Unauthorized`

### 4.4 에러 케이스

| 상황 | HTTP Status | errorCode | message |
|------|-------------|-----------|---------|
| 상품 미존재 | 404 | Not Found | 해당 상품을 찾을 수 없습니다 |
| 이미 좋아요함 | 409 | Conflict | 이미 좋아요한 상품입니다 |
| 좋아요 기록 없음 | 404 | Not Found | 좋아요 기록을 찾을 수 없습니다 |
| 인증 실패 | 401 | Unauthorized | 인증 정보가 유효하지 않습니다 |

---

## 5. Order / OrderItem 도메인

### 5.1 API 목록

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/orders` | 주문 생성 | 필요 |
| GET | `/api/v1/orders/{orderId}` | 주문 단건 조회 | 필요 |
| GET | `/api/v1/orders/me` | 내 주문 목록 조회 | 필요 |
| PATCH | `/api/v1/orders/{orderId}/cancel` | 주문 취소 | 필요 |

### 5.2 필드 정의

#### Order

| 필드명 | 타입 | 제약조건 | 검증 규칙 |
|--------|------|----------|-----------|
| id | Long | PK, AUTO_INCREMENT | BaseEntity |
| userId | Long | FK(user.id), NOT NULL | 주문자 |
| status | OrderStatus (Enum) | NOT NULL | 초기값: ORDERED |
| totalPrice | Long | NOT NULL | 주문 항목 합산, 0 이상 |
| orderedAt | ZonedDateTime | NOT NULL | 주문 시점 |
| createdAt | ZonedDateTime | NOT NULL | BaseEntity |
| updatedAt | ZonedDateTime | NOT NULL | BaseEntity |
| deletedAt | ZonedDateTime | nullable | BaseEntity (Soft Delete) |

#### OrderStatus (Enum)

| 값 | 설명 | 전이 가능 상태 |
|----|------|----------------|
| ORDERED | 주문 완료 | CANCELLED |
| CANCELLED | 주문 취소 | (종료 상태) |

#### OrderItem

| 필드명 | 타입 | 제약조건 | 검증 규칙 |
|--------|------|----------|-----------|
| id | Long | PK, AUTO_INCREMENT | BaseEntity |
| orderId | Long | FK(order.id), NOT NULL | 소속 주문 |
| productId | Long | FK(product.id), NOT NULL | 주문 상품 |
| quantity | Integer | NOT NULL | 1 이상 |
| price | Long | NOT NULL | 주문 시점 상품 가격 |
| createdAt | ZonedDateTime | NOT NULL | BaseEntity |
| updatedAt | ZonedDateTime | NOT NULL | BaseEntity |
| deletedAt | ZonedDateTime | nullable | BaseEntity (Soft Delete) |

### 5.3 유스케이스

#### UC-O01: 주문 생성

**Main Flow**
1. 회원이 주문할 상품 목록(productId, quantity)을 전달하여 주문을 요청한다.
2. 시스템은 각 상품이 존재하는지 확인한다.
3. 시스템은 각 상품의 재고가 충분한지 확인한다.
4. 시스템은 주문 시점의 상품 가격으로 OrderItem을 생성한다.
5. 시스템은 totalPrice를 계산한다 (각 항목의 price * quantity 합산).
6. 주문 상태를 ORDERED로 설정하고 저장한다.
7. 각 상품의 재고를 차감한다.
8. 생성된 주문 정보를 응답한다.

**Exception Flow**
- E1: 주문 항목이 비어 있음 → `400 Bad Request`
- E2: 상품이 존재하지 않음 → `404 Not Found`
- E3: 재고 부족 → `400 Bad Request`
- E4: 인증 실패 → `401 Unauthorized`

#### UC-O02: 주문 단건 조회

**Main Flow**
1. 회원이 orderId로 주문 정보를 조회한다.
2. 시스템은 해당 주문이 본인의 주문인지 확인한다.
3. 주문 정보 + OrderItem 목록을 응답한다.

**Exception Flow**
- E1: 해당 주문이 존재하지 않음 → `404 Not Found`
- E2: 본인의 주문이 아님 → `404 Not Found` (본인 주문만 조회 가능하도록 쿼리)
- E3: 인증 실패 → `401 Unauthorized`

#### UC-O03: 내 주문 목록 조회

**Main Flow**
1. 회원이 자신의 주문 목록을 조회한다.
2. 시스템은 해당 회원의 주문 목록을 반환한다.

**Alternate Flow**
- A1: 주문이 없으면 빈 리스트를 반환한다.

**Exception Flow**
- E1: 인증 실패 → `401 Unauthorized`

#### UC-O04: 주문 취소

**Main Flow**
1. 회원이 특정 주문의 취소를 요청한다.
2. 시스템은 해당 주문이 본인의 주문인지 확인한다.
3. 시스템은 주문 상태가 ORDERED인지 확인한다.
4. 주문 상태를 CANCELLED로 변경한다.
5. 차감되었던 재고를 복원한다.
6. 결과를 응답한다.

**Exception Flow**
- E1: 주문이 존재하지 않음 → `404 Not Found`
- E2: 본인의 주문이 아님 → `404 Not Found` (본인 주문만 조회 가능하도록 쿼리)
- E3: 이미 취소된 주문 → `400 Bad Request`
- E4: 인증 실패 → `401 Unauthorized`

### 5.4 에러 케이스

| 상황 | HTTP Status | errorCode | message |
|------|-------------|-----------|---------|
| 주문 항목 비어 있음 | 400 | Bad Request | 주문 항목은 최소 1개 이상이어야 합니다 |
| 상품 미존재 | 404 | Not Found | 해당 상품을 찾을 수 없습니다 |
| 재고 부족 | 400 | Bad Request | 재고가 부족합니다 |
| 주문 미존재 | 404 | Not Found | 해당 주문을 찾을 수 없습니다 |
| 본인 주문 아님 (또는 미존재) | 404 | Not Found | 해당 주문을 찾을 수 없습니다 |
| 이미 취소된 주문 | 400 | Bad Request | 이미 취소된 주문입니다 |
| 인증 실패 | 401 | Unauthorized | 인증 정보가 유효하지 않습니다 |

---

## 6. 공통 정책

### 6.1 인증

- 인증이 필요한 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 인증한다.
- 인증 실패 시 `401 Unauthorized`를 반환한다.
- 1주차 User 도메인의 인증 방식을 그대로 사용한다.

### 6.2 응답 형식

- 모든 API는 `ApiResponse<T>` 형식으로 응답한다.
- 성공: `meta.result = SUCCESS`, `data = 응답 데이터`
- 실패: `meta.result = FAIL`, `meta.errorCode`, `meta.message` 포함

### 6.3 Soft Delete

- 모든 엔티티는 BaseEntity를 상속하며, `deletedAt` 필드로 Soft Delete를 지원한다.
- 조회 시 deletedAt이 null인 데이터만 반환한다.

### 6.4 검증 순서

1. 인증 검증 (헤더)
2. 요청 형식 검증 (Jakarta Validation)
3. 비즈니스 규칙 검증 (도메인 서비스)

---

## 7. 설계 결정 사항

> 애매한 요구사항을 선택지 + 영향도 형태로 정리하고, 확정된 결정을 기록한다.

### 정책 결정

#### Q-L01: 좋아요 취소 방식 — Hard Delete 채택

| 구분 | 내용 |
|------|------|
| **채택** | **B: Hard Delete (물리 삭제)** |
| 선택 이유 | (userId, productId) UNIQUE 제약이 단순해지고, 재좋아요 시 restore 로직이 불필요하다 |
| 트레이드오프 | 좋아요 이력 추적 불가. 향후 이력 분석 필요 시 별도 이벤트 로그 테이블 필요 |
| 고려되었으나 채택하지 않음 | A: Soft Delete — 재좋아요 시 restore 로직 필요, UNIQUE 제약에 deletedAt 조건 결합으로 복잡도 증가 |

#### Q-O01: 타인 주문 접근 시 응답 코드 — 404 Not Found 채택

| 구분 | 내용 |
|------|------|
| **채택** | **B: 404 Not Found** |
| 선택 이유 | orderId 존재 여부가 노출되지 않아 보안상 안전. `findByIdAndUserId` 단일 쿼리로 구현 단순화 |
| 트레이드오프 | 디버깅 시 "권한 문제 vs 미존재" 구분 어려움. 서버 로그로 보완 가능 |
| 고려되었으나 채택하지 않음 | A: 403 Forbidden — 의미론적으로 정확하지만 orderId 존재 여부 노출 |

#### Q-O02: 주문 상태 범위 — ORDERED, CANCELLED만 채택

| 구분 | 내용 |
|------|------|
| **채택** | **A: ORDERED, CANCELLED만** |
| 선택 이유 | 배송/완료 흐름이 현재 요구사항에 없음. 상태 2개면 전이 규칙이 단순 (ORDERED → CANCELLED) |
| 트레이드오프 | 향후 배송 추적 시 Enum 확장 + 전이 로직 추가 필요. 다만 Enum 값 추가는 하위 호환 가능 |
| 고려되었으나 채택하지 않음 | B: ORDERED → SHIPPING → DELIVERED / CANCELLED — 구현 복잡도 증가, 상태 전이 검증 로직 필요 |

### 경계 결정

#### Q-P01: 상품 가격 범위 — 0 이상 채택

| 구분 | 내용 |
|------|------|
| **채택** | **A: 0 이상 (무료 상품 허용)** |
| 선택 이유 | 무료 상품을 막을 비즈니스 근거 없음. 검증 로직 `price >= 0`으로 단순 |
| 트레이드오프 | 0원 주문 발생 가능. 필요 시 주문 도메인 정책에서 별도 검증으로 분리 가능 |
| 고려되었으나 채택하지 않음 | B: 1 이상 — 무료 상품 지원 불가, 근거 없는 제약 |

#### Q-P02: 재고 0인 상품 노출 — 조회에 노출 채택

| 구분 | 내용 |
|------|------|
| **채택** | **A: 조회에 노출 (재고 0 포함)** |
| 선택 이유 | 목록/상세 조회는 상품 정보 확인 목적. 재고 필터링은 주문 시점에 검증하면 됨 |
| 트레이드오프 | 재고 없는 상품을 보고 주문 시도 가능. 주문 생성 시 재고 부족 에러로 명확히 반환 |
| 고려되었으나 채택하지 않음 | B: 조회에서 제외 — 쿼리 조건 추가로 복잡도 증가 |

#### Q-O03: 주문 취소 시 재고 복원 — 복원 채택

| 구분 | 내용 |
|------|------|
| **채택** | **A: 복원한다** |
| 선택 이유 | 데이터 정합성은 복잡도를 이유로 타협할 수 없음 |
| 트레이드오프 | 주문 취소 트랜잭션이 Order + Product를 함께 수정 (트랜잭션 비대화). Facade에서 두 Service를 조합하여 책임 분리 가능 |
| 고려되었으나 채택하지 않음 | B: 복원하지 않음 — 재고 정합성 깨짐, 데이터 신뢰도 하락 |

### 확장 결정

#### Q-E01: 페이징 — 페이징 없음 채택

| 구분 | 내용 |
|------|------|
| **채택** | **A: 전체 조회 (페이징 없음)** |
| 선택 이유 | 최소 구현 범위에서 데이터 양 제한적. 페이징 적용 시 요청 파라미터 + 응답 구조 + Pageable 처리가 모든 목록 API에 추가 |
| 트레이드오프 | 데이터 수천 건 이상 시 성능 이슈. `List<T>` → `Page<T>` 변경은 비교적 단순하여 이후 추가 가능 |
| 고려되었으나 채택하지 않음 | B: 페이징 적용 — 구현량 대비 현 시점 효용 낮음 |

#### Q-E02: 상품 수정/삭제 API — 제외 채택

| 구분 | 내용 |
|------|------|
| **채택** | **B: 제외** |
| 선택 이유 | 과제 명세에 미명시. 등록 + 조회만으로 핵심 흐름(등록 → 좋아요 → 주문) 완성 가능 |
| 트레이드오프 | 등록한 상품의 정보 수정/삭제 불가. 필요 시 별도 추가하며 기존 설계에 영향 없음 |
| 고려되었으나 채택하지 않음 | A: 포함 — 불필요한 API 증가, 시퀀스/클래스/ERD 문서량 증가 |

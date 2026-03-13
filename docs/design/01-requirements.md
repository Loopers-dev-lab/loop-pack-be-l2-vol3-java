# 요구사항 명세서

## 1. 개요

이커머스 플랫폼의 핵심 도메인(브랜드, 상품, 좋아요, 주문)에 대한 요구사항을 정의한다.

---

## 2. 액터 정의

| 액터 | 설명 | 인증 방식 |
|------|------|----------|
| **User (회원)** | 상품을 조회하고 좋아요, 주문을 수행하는 일반 사용자 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 |
| **Admin (관리자)** | 브랜드/상품을 등록·수정·삭제하고, 전체 주문을 조회하는 운영자 | `X-Loopers-Ldap: loopers.admin` 헤더 |
| **System** | 배치 작업, 정합성 보정 등 내부 시스템 프로세스 | 내부 호출 (인증 없음) |

---

## 3. 유비쿼터스 언어 (Ubiquitous Language)

| 용어 | 정의 |
|------|------|
| **브랜드 (Brand)** | 상품을 판매하는 판매자/제조사 단위 |
| **상품 (Product)** | 판매되는 개별 품목. 하나의 브랜드에 속함 |
| **재고 (Stock)** | 상품의 판매 가능 수량 |
| **좋아요 (Like)** | 회원이 상품에 표시한 관심 표시 |
| **주문 (Order)** | 회원이 상품을 구매하기 위해 생성한 거래 단위 |
| **주문 항목 (OrderItem)** | 주문에 포함된 개별 상품 정보 (스냅샷 포함) |
| **스냅샷 (Snapshot)** | 주문 시점의 상품 정보를 보존한 데이터 |

---

## 4. 도메인별 기능 요구사항

### 4.1 브랜드 (Brand)

#### US-B01: 브랜드 등록
```
As a 관리자
I want to 새로운 브랜드를 등록하고 싶다
So that 해당 브랜드의 상품을 등록할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 브랜드명, 설명을 입력하여 브랜드를 생성한다 |
| Alternate | - |
| Exception | 브랜드명이 비어있으면 등록 실패 |

#### US-B02: 브랜드 목록 조회
```
As a 사용자
I want to 브랜드 목록을 조회하고 싶다
So that 원하는 브랜드의 상품을 찾을 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 등록된 브랜드 목록을 조회한다 (삭제되지 않은 것만) |
| Alternate | - |
| Exception | - |

#### US-B03: 브랜드 삭제
```
As a 관리자
I want to 브랜드를 삭제하고 싶다
So that 더 이상 해당 브랜드의 상품이 노출되지 않는다
```

| 흐름 | 설명 |
|------|------|
| Main | 브랜드를 soft delete 처리한다 |
| Alternate | 해당 브랜드의 모든 상품도 soft delete 처리된다 |
| Alternate | 해당 상품들의 좋아요는 hard delete 된다 |
| Exception | 존재하지 않는 브랜드이면 삭제 실패 |

---

### 4.2 상품 (Product)

#### US-P01: 상품 등록
```
As a 관리자
I want to 새로운 상품을 등록하고 싶다
So that 회원들이 해당 상품을 구매할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 상품명, 가격, 재고, 브랜드를 입력하여 상품을 생성한다 |
| Alternate | - |
| Exception | 가격이 0 이하이면 등록 실패 |
| Exception | 재고가 음수이면 등록 실패 |
| Exception | 존재하지 않는 브랜드이면 등록 실패 |

#### US-P02: 상품 목록 조회
```
As a 사용자
I want to 상품 목록을 조회하고 싶다
So that 구매할 상품을 선택할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 상품 목록을 조회한다 (삭제되지 않은 것만) |
| Alternate | 좋아요 순 정렬 가능 |
| Alternate | 브랜드별 필터링 가능 |
| Exception | - |

#### US-P03: 상품 상세 조회
```
As a 사용자
I want to 상품 상세 정보를 조회하고 싶다
So that 상품 정보를 확인하고 구매를 결정할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 상품의 상세 정보(이름, 가격, 재고, 브랜드, 좋아요 수)를 조회한다 |
| Alternate | - |
| Exception | 존재하지 않거나 삭제된 상품이면 조회 실패 |

#### US-P04: 상품 수정
```
As a 관리자
I want to 상품 정보를 수정하고 싶다
So that 변경된 정보를 반영할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 상품명, 가격, 재고를 수정한다 |
| Alternate | - |
| Exception | 가격이 0 이하이면 수정 실패 |
| Exception | 재고가 음수이면 수정 실패 |

#### US-P05: 상품 삭제
```
As a 관리자
I want to 상품을 삭제하고 싶다
So that 더 이상 해당 상품이 노출되지 않는다
```

| 흐름 | 설명 |
|------|------|
| Main | 상품을 soft delete 처리한다 |
| Alternate | 해당 상품의 좋아요는 hard delete 된다 |
| Exception | 존재하지 않는 상품이면 삭제 실패 |

---

### 4.3 좋아요 (Like)

#### US-L01: 좋아요 등록
```
As a 회원
I want to 상품에 좋아요를 등록하고 싶다
So that 관심 상품을 표시할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | POST `/api/v1/products/{productId}/likes` - 상품에 좋아요를 추가하고, 상품의 좋아요 수를 증가시킨다 |
| Alternate | 이미 좋아요한 상품이면 아무 동작 없음 (멱등성 보장) |
| Exception | 존재하지 않거나 삭제된 상품이면 실패 |

#### US-L02: 좋아요 취소
```
As a 회원
I want to 좋아요를 취소하고 싶다
So that 관심 상품에서 제외할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | DELETE `/api/v1/products/{productId}/likes` - 좋아요를 삭제하고, 상품의 좋아요 수를 감소시킨다 |
| Alternate | 좋아요하지 않은 상품이면 아무 동작 없음 (멱등성 보장) |
| Exception | - |

#### US-L03: 내가 좋아요한 상품 목록 조회
```
As a 회원
I want to 내가 좋아요한 상품 목록을 조회하고 싶다
So that 관심 상품을 한눈에 확인할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | GET `/api/v1/users/{userId}/likes` - 해당 회원이 좋아요한 상품 목록을 조회한다 |
| Alternate | 좋아요한 상품이 없으면 빈 목록 반환 |
| Exception | 다른 회원의 좋아요 목록 조회 시 권한 검증 (본인만 조회 가능) |

---

### 4.4 주문 (Order)

#### US-O01: 주문 생성
```
As a 회원
I want to 상품을 주문하고 싶다
So that 상품을 구매할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 1. 주문할 상품과 수량을 선택한다 |
| Main | 2. 재고를 확인하고 차감한다 |
| Main | 3. 주문을 생성하고 주문 항목에 스냅샷을 저장한다 |
| Alternate | 여러 상품을 한 번에 주문할 수 있다 |
| Exception | 재고가 부족하면 주문 실패 |
| Exception | 존재하지 않거나 삭제된 상품이면 주문 실패 |

#### US-O02: 주문 목록 조회
```
As a 회원
I want to 내 주문 목록을 조회하고 싶다
So that 주문 이력을 확인할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | GET `/api/v1/orders?startAt=&endAt=` - 해당 회원의 주문 목록을 조회한다 |
| Alternate | `startAt`, `endAt` 파라미터로 날짜 범위 필터링 가능 |
| Exception | - |

#### US-O03: 주문 상세 조회
```
As a 회원
I want to 주문 상세 내역을 조회하고 싶다
So that 주문한 상품과 금액을 확인할 수 있다
```

| 흐름 | 설명 |
|------|------|
| Main | 주문 항목의 스냅샷 정보를 포함하여 조회한다 |
| Alternate | - |
| Exception | 다른 회원의 주문이면 조회 실패 |

---

## 5. 설계 결정 사항

### 5.1 재고 차감 시점

| 결정 | 주문 생성 시 즉시 차감 (단일 트랜잭션) |
|------|------|
| **이유** | 현재 과제는 모노리스 + 결제 미구현 상태 |
| **방식** | 단일 트랜잭션으로 `재고 확인 → 주문 저장 → 재고 차감`을 원자적으로 처리 |
| **확장** | 결제가 추가되면 보상 트랜잭션(Saga) 고려 |

```
현재: Order 생성 시 Stock 차감 (같은 트랜잭션)
미래: Order 생성 → Payment 요청 → [실패 시] Stock 복원
```

### 5.2 상품 스냅샷 범위

**판단 기준**: "주문 상세 화면을 독립적으로 렌더링할 수 있는가?"

원본 데이터가 변경되거나 삭제되어도, 주문 상세 페이지가 깨지지 않고 온전하게 보여야 한다.
(예: 쿠팡에서 3년 전 주문 내역을 열면 단종된 상품이라도 당시 상품명, 가격이 다 보임)

| 분류 | 항목 | 저장 여부 | 이유 |
|------|------|----------|------|
| **필수** | product_name | O | 없으면 주문 상세 화면 성립 불가. 변경 시 "내가 주문한 게 이게 아닌데" 클레임 |
| **필수** | product_price | O | 정산/환불 기준. 변경되면 금액 증빙 불가 |
| **필수** | quantity | O | 주문 수량 |
| **권장** | brand_name | O | 주문 내역 UI에 거의 항상 표시 |
| **제외** | image_url | X | 현재 상품 스펙에 이미지 필드 없음. 요구사항에 없는 필드를 미리 스냅샷에 넣는 건 오버엔지니어링 |
| **제외** | description | X | 주문 상세에서 보여줄 필요 없음. 상품 상세 페이지 영역 |
| **제외** | like_count | X | 주문 내역과 무관 |
| **제외** | stock_quantity | X | 주문 내역과 무관 |

**트레이드오프**: 스냅샷 컬럼이 늘어날수록 저장 비용 증가, 원본과의 동기화 불일치 가능성 증가, 스키마 변경 시 마이그레이션 영향 범위 확대

### 5.3 주문 상태

```java
public enum OrderStatus {
    CREATED,      // 주문 생성됨 (현재는 이게 곧 완료)
    PAID,         // 결제 완료 (미래 확장용)
    CANCELLED     // 취소됨
}
```

- 결제가 없는 현재: `CREATED` = 주문 완료 상태로 사용
- 결제가 추가되면: `CREATED` → `PAID` 전이 추가
- YAGNI 원칙에 따라 현재 로직에서는 PAID를 사용하지 않음

### 5.4 좋아요 수 관리

| 결정 | 별도 컬럼 (like_count) + 동기화 |
|------|------|
| **이유** | `likes_desc` 정렬 요구사항 → 매 조회 시 COUNT는 비효율 |
| **허용 오차** | 좋아요 수는 1~2개 오차 허용 가능 (재고와 달리 "틀리면 큰일나는" 데이터 아님) |
| **정합성** | 같은 트랜잭션 처리, 필요시 배치로 보정 |

```java
@Transactional
public void addLike(Long memberId, Long productId) {
    likeRepository.save(new Like(memberId, productId));
    productRepository.incrementLikeCount(productId);  // UPDATE +1
}
```

### 5.5 삭제 정책

| 테이블 | 삭제 방식 | 이유 |
|--------|-----------|------|
| brands | Soft Delete | 상품이 참조, 주문 이력 보존 |
| products | Soft Delete | 주문이 참조 (스냅샷 있어도 조회 가능해야) |
| likes | Hard Delete | 삭제된 상품 좋아요는 의미 없음 |
| orders | Soft Delete | 주문 이력은 절대 삭제 안 함 |
| order_items | 삭제 없음 | Order와 생명주기 공유 (Order 취소 시에도 보존) |

**브랜드 삭제 시 연쇄 처리**:
```java
@Transactional
public void deleteBrand(Long brandId) {
    // 1. 해당 브랜드의 모든 상품 soft delete
    productRepository.softDeleteByBrandId(brandId);
    // 2. 해당 상품들의 좋아요 hard delete
    likeRepository.deleteByBrandId(brandId);
    // 3. 브랜드 soft delete
    brandRepository.softDelete(brandId);
}
```

---

## 6. API 명세

### 6.1 인증 방식

| 구분 | Prefix | 인증 헤더 |
|------|--------|----------|
| 대고객 API | `/api/v1` | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| 어드민 API | `/api-admin/v1` | `X-Loopers-Ldap: loopers.admin` |

### 6.2 브랜드 & 상품 (대고객)

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/brands/{brandId}` | 브랜드 정보 조회 |
| GET | `/api/v1/products` | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | 상품 정보 조회 |

**상품 목록 조회 쿼리 파라미터:**
- `brandId`: 브랜드별 필터링
- `sort`: 정렬 (latest/price_asc/likes_desc)
- `page`, `size`: 페이징

### 6.3 브랜드 & 상품 (Admin)

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/brands` | 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 (상품도 삭제) |
| GET | `/api-admin/v1/products` | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | 상품 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

### 6.4 좋아요 (Likes)

| METHOD | URI | 설명 |
|--------|-----|------|
| POST | `/api/v1/products/{productId}/likes` | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | 내가 좋아요 한 상품 목록 |

### 6.5 주문 (Orders)

| METHOD | URI | 설명 |
|--------|-----|------|
| POST | `/api/v1/orders` | 주문 요청 |
| GET | `/api/v1/orders?startAt=&endAt=` | 주문 목록 조회 (날짜 필터) |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 조회 |

**주문 요청 Body 예시:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

**주문 시 필수 처리:**
- 스냅샷 저장 (상품명, 가격, 브랜드명)
- 재고 확인 및 차감

### 6.6 주문 (Admin)

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders` | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | 주문 상세 조회 |

---

## 7. 비기능 요구사항

| 항목 | 요구사항 |
|------|----------|
| 트랜잭션 | 주문 생성 시 재고 차감은 원자적으로 처리 |
| 멱등성 | 좋아요 등록/취소는 멱등하게 동작 |
| 정합성 | 주문 취소 시 재고 복원 보장 |
| 데이터 보존 | 주문 관련 데이터는 soft delete로 보존 |

---

## 8. 미결정 사항 (추후 결정 필요)

| 항목 | 현재 상태 | 추후 결정 시점 |
|------|----------|--------------|
| **결제 연동** | 미구현 (주문 생성 = 완료) | 결제 시스템 도입 시 |
| **동시성 제어** | 고려하지 않음 | 트래픽 증가 시 낙관적/비관적 락 선택 |
| **멱등성 키** | 미구현 | 중복 주문 방지 필요 시 |
| **일관성 보장** | 단일 트랜잭션 | MSA 전환 시 Saga 패턴 고려 |
| **느린 조회 최적화** | 기본 인덱스만 | 대량 데이터 시 캐시/검색엔진 도입 |
| **주문 상태 확장** | CREATED/PAID/CANCELLED | 배송 상태 추가 시 확장 |
| **Admin 인증 강화** | 단순 LDAP 헤더 | 실서비스 시 JWT/OAuth 전환 |

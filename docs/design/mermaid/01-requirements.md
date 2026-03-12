# 이커머스 요구사항 정의서

---

## 1. Brand (브랜드)

### 유저 스토리

- 고객은 브랜드 정보를 조회할 수 있다.
- 어드민은 새로운 브랜드를 등록할 수 있다.
- 어드민은 브랜드 정보(이름, 설명)를 수정할 수 있다.
- 어드민은 브랜드를 삭제할 수 있다.

### 기능 흐름

**브랜드 등록**

1. 어드민 인증 확인 (@AdminUser)
2. 브랜드명 유효성 검증 (빈값 불가)
3. 브랜드명 중복 체크 (Q17: 중복 불가 → 409 Conflict)
4. 브랜드 생성 및 저장
5. 등록 결과 반환

**브랜드 수정**

1. 어드민 인증 확인
2. 브랜드 존재 여부 확인 (없으면 404)
3. 브랜드명 변경 시 중복 체크
4. name, description 수정 및 저장
5. 소속 상품에는 별도 업데이트 불필요 (Q31: brandId 참조 방식으로 자동 반영)

**브랜드 삭제 (연쇄)**

1. 어드민 인증 확인
2. 브랜드 존재 여부 확인 (없으면 404)
3. 해당 브랜드 소속 상품 전체 soft delete (Q1: Soft Delete 연쇄)
4. 브랜드 soft delete
5. 트랜잭션 커밋 (전체 하나의 트랜잭션)

### 비즈니스 규칙

- 브랜드명은 유니크해야 한다 (Q17)
- 브랜드 삭제 시 소속 상품도 함께 soft delete (Q1)
- 브랜드-상품은 ID 참조만 사용, JPA 연관관계 없음 (ADR-008)
- 브랜드 restore는 설계 범위에서 제외 (Q2)
- 삭제된 브랜드에 새 상품 등록 불가 (Q27)
- 브랜드명 수정 시 소속 상품에 자동 반영됨 — brandId 참조 방식 (Q31)
- 브랜드 필드: name(필수, BrandName VO), description(선택) (Q14)

### API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/v1/brands/{brandId}` | 고객 브랜드 정보 조회 | 불필요 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 | @AdminUser |
| GET | `/api-admin/v1/brands?page=0&size=20` | 어드민 브랜드 목록 (삭제 포함) | @AdminUser |
| GET | `/api-admin/v1/brands/{brandId}` | 어드민 브랜드 상세 | @AdminUser |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 | @AdminUser |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 | @AdminUser |

---

## 2. Product + Stock (상품 + 재고)

### 유저 스토리

- 고객은 상품 목록을 조회할 수 있다.
- 고객은 상품 상세 정보를 볼 수 있다.
- 어드민은 새로운 상품을 등록할 수 있다.
- 어드민은 상품 정보를 수정할 수 있다.
- 어드민은 상품을 삭제할 수 있다.

### 기능 흐름

**상품 등록**

1. 어드민 인증 확인 (@AdminUser)
2. 브랜드 존재 여부 확인 (삭제된 브랜드 불가, Q27)
3. 상품 정보 검증 (name 필수, price >= 0)
4. 상품 생성 및 저장
5. Stock 생성 (initialStock 수량, Product와 1:1, Q4)
6. 하나의 트랜잭션으로 처리

**상품 목록 조회 (Customer)**

1. 정렬 파라미터 파싱 (Q24: created_desc / price_asc / price_desc / likes_desc)
2. 페이지네이션 적용 (Q15: Spring Pageable)
3. 삭제된 상품 제외 (deletedAt IS NULL)
4. 브랜드 필터 적용 (선택)
5. Stock 정보 조합
6. 재고 상태 변환 (Q6: >10 → IN_STOCK, 1~10 → LOW_STOCK, 0 → OUT_OF_STOCK)

**상품 목록 조회 (Admin)**

1. 어드민 인증 확인
2. 삭제된 상품도 포함하여 조회 가능
3. 정확한 재고 수량 표시 (Q6)
4. createdAt, updatedAt, deletedAt 포함

**상품 수정 (Admin)**

1. 어드민 인증 확인
2. 상품 존재 여부 확인
3. 수정 가능 필드만 변경: name, description, price (Q29)
4. 수정 불가 필드: brandId(브랜드 이동 불가), likeCount(시스템 관리) (Q29)
5. 저장

### 비즈니스 규칙

- 상품 필드: name, description, price(Money VO), brandId 필수 (Q3)
- 재고는 별도 Stock 엔티티로 분리 — Product와 1:1 (Q4)
- 가격은 Money VO (int 내부 타입, 원화, 음수 불가, 0원 허용) (Q5)
- 고객에게는 재고 상태(IN_STOCK/LOW_STOCK/OUT_OF_STOCK)만 표시 (Q6)
- 어드민에게는 정확한 재고 수량 표시 (Q6)
- 상품명 중복 허용 (Q18)
- 수정 가능: name, description, price / 수정 불가: brandId, likeCount (Q29)
- 삭제된 브랜드에 상품 등록 불가 (Q27)

### 정렬 옵션 (Q24)

| 정렬 값 | 동작 |
|---------|------|
| `created_desc` (기본) | 상품 등록일 최신순 |
| `price_asc` | 가격 낮은순 |
| `price_desc` | 가격 높은순 |
| `likes_desc` | 좋아요 많은순 |

### API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/v1/products?brandId={brandId}&sort={sort}&page=0&size=20` | 고객 상품 목록 | 불필요 |
| GET | `/api/v1/products/{productId}` | 고객 상품 상세 | 불필요 |
| POST | `/api-admin/v1/products` | 상품 등록 (initialStock 포함) | @AdminUser |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | 어드민 상품 목록 (삭제 포함) | @AdminUser |
| GET | `/api-admin/v1/products/{productId}` | 어드민 상품 상세 | @AdminUser |
| PUT | `/api-admin/v1/products/{productId}` | 상품 수정 | @AdminUser |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 | @AdminUser |

---

## 3. Like (좋아요)

### 유저 스토리

- 사용자는 상품을 찜할 수 있다.
- 이미 찜한 상품을 다시 누르면 무시된다 (멱등성).
- 사용자는 찜을 취소할 수 있다.
- 이미 취소한 찜을 다시 취소하면 무시된다 (멱등성).
- 사용자는 찜한 상품 목록을 볼 수 있다.

### 기능 흐름

**좋아요 추가**

1. 로그인 사용자만 가능 (@LoginMember)
2. 상품 존재 여부 확인 (삭제된 상품 불가, Q16)
3. 기존 좋아요 존재 여부 판단
4. 없으면 좋아요 저장 + likeCount 증가
5. 있으면 아무 동작 없이 200 OK 반환 (Q7: 멱등성)

**좋아요 취소**

1. 로그인 사용자만 가능
2. 기존 좋아요 존재 여부 판단
3. 있으면 삭제 + likeCount 감소 (음수 방지, Q28)
4. 없으면 아무 동작 없이 200 OK 반환 (Q7: 멱등성)

**좋아요 목록 조회**

1. 로그인 사용자만 가능
2. 본인의 좋아요 목록만 조회
3. 삭제된 상품은 목록에서 제외 (Q16)
4. 좋아요 누른 시간 최신순 정렬 (Q23)
5. 페이지네이션 적용

### 비즈니스 규칙

- 좋아요 등록/취소 모두 멱등성 보장, 200 OK 반환 (Q7)
- Product 테이블에 likeCount 비정규화 유지 (Q8)
- likeCount는 0 미만이 될 수 없다 — 애플리케이션 가드 + DB CHECK (Q28)
- 삭제된 상품에는 좋아요 불가 (Q16)
- 삭제된 상품은 좋아요 목록에서 제외 (Q16)
- 좋아요 목록은 좋아요 누른 시간 최신순 (Q23)
- 좋아요 데이터 자체는 삭제하지 않음 (상품 복구 시 다시 보임)

### API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/v1/products/{productId}/likes` | 좋아요 추가 | @LoginMember |
| DELETE | `/api/v1/products/{productId}/likes` | 좋아요 취소 | @LoginMember |
| GET | `/api/v1/users/{userId}/likes` | 내 좋아요 목록 | @LoginMember |

---

## 4. Order (주문)

### 유저 스토리

- 고객은 여러 상품을 한 번에 주문할 수 있다.
- 주문하면 재고가 즉시 차감된다.
- 주문 정보에는 당시의 상품 정보가 스냅샷으로 저장된다.
- 고객은 자신의 주문 내역을 조회할 수 있다.
- 고객은 주문 상세(상품별 수량, 금액)를 볼 수 있다.
- 어드민은 전체 주문을 조회할 수 있다.

### 기능 흐름

**주문 생성**

1. 로그인 사용자만 가능 (@LoginMember)
2. 각 주문 상품에 대해:
   a. 상품 존재 여부 확인 — 삭제된 상품 포함 시 전체 실패 (Q20)
   b. 수량 검증 — 1 이상 (Q26)
   c. 재고 확인 및 차감 — 부족 시 전체 실패 (Q19)
3. Order 생성 (status = CREATED, Q11)
4. OrderItem 생성 — 상품명, 가격 스냅샷 저장 (Q9)
5. 총액은 서버에서 계산하여 Order에 저장 (Q12)
6. 전체 하나의 트랜잭션 — 실패 시 전체 롤백 (Q19: All or Nothing)

**주문 목록 조회**

1. 고객: 본인 주문만 조회 (Q25: userId 필터)
2. 날짜 범위 필터 적용 (startAt, endAt 쿼리 파라미터)
3. 어드민: 전체 주문 조회 가능 (Q25)
4. 최신순 정렬, 페이지네이션 적용

**주문 상세 조회**

1. 고객: 본인 주문만 상세 조회 가능
2. OrderItem 목록 포함 (스냅샷된 상품명, 가격, 수량, 소계)
3. 상품이 삭제된 후에도 스냅샷으로 조회 가능

### 비즈니스 규칙

- Order + OrderItem 분리 (Q10: 1:N 관계)
- OrderItem에 주문 시점의 상품명, 가격, 수량 스냅샷 저장 (Q9)
- OrderStatus: CREATED만 사용, 나머지(CONFIRMED, SHIPPING, DELIVERED, CANCELLED)는 미래 확장용 (Q11)
- 총액은 서버에서 계산, 클라이언트 전송값 무시 (Q12)
- 재고 부족 시 주문 전체 실패 — All or Nothing (Q19)
- 삭제된 상품 포함 시 전체 실패 + 상세 에러 메시지 (Q20)
- 주문 생성과 동시에 재고 차감, 하나의 트랜잭션 (Q21)
- 자기 자신 주문 제한 없음 (Q22: 어드민/고객 인증 체계 분리)
- 고객은 본인 주문만, 어드민은 전체 조회 가능 (Q25)
- 수량은 최소 1, 최대 해당 상품 재고 이하 (Q26)

### API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/v1/orders` | 주문 생성 | @LoginMember |
| GET | `/api/v1/orders?startAt={date}&endAt={date}` | 내 주문 목록 (날짜 범위 필터) | @LoginMember |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 | @LoginMember |
| GET | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록 | @AdminUser |
| GET | `/api-admin/v1/orders/{orderId}` | 주문 상세 | @AdminUser |

---

## 5. 공통

### 인증 (Q13: ArgumentResolver)

- `@LoginMember` — 고객 인증 (X-Loopers-LoginId + X-Loopers-LoginPw → MemberModel 주입)
- `@AdminUser` — 어드민 인증 (X-Loopers-Ldap → AdminInfo 주입)

### 페이지네이션 (Q15)

- Spring Pageable 사용
- 공통 파라미터: page, size
- JPA Repository와 자연스럽게 연동

### 에러 처리 (Q32)

기존 ErrorType 4종(NOT_FOUND, BAD_REQUEST, CONFLICT, INTERNAL_ERROR)을 활용하고, 메시지로 상황을 구분한다.

| 상황 | ErrorType | 메시지 예시 |
|------|-----------|------------|
| 존재하지 않는 상품/브랜드/주문 | NOT_FOUND | "상품을 찾을 수 없습니다" |
| 브랜드명 중복 | CONFLICT | "이미 존재하는 브랜드명입니다" |
| 재고 부족 | BAD_REQUEST | "재고가 부족합니다: [상품명]" |
| 주문 수량 0 이하 | BAD_REQUEST | "주문 수량은 1 이상이어야 합니다" |
| 삭제된 상품에 좋아요 | NOT_FOUND | "상품을 찾을 수 없습니다" |
| 삭제된 상품이 주문에 포함 | NOT_FOUND | "삭제된 상품이 포함되어 있습니다: [상품명]" |

---

## 6. API 엔드포인트 요약

| 구분 | Customer | Admin | 합계 |
|------|----------|-------|------|
| Brand | 1 | 5 | 6 |
| Product | 2 | 5 | 7 |
| Like | 3 | 0 | 3 |
| Order | 3 | 2 | 5 |
| **합계** | **9** | **12** | **21** |

---

## 7. Q&A 트레이드오프 추적표

| Q# | 결정 사항 | 반영 위치 |
|----|-----------|-----------|
| Q1 | Soft Delete 연쇄 | Brand 비즈니스 규칙, 기능 흐름 |
| Q2 | 브랜드 restore 제외 | Brand 비즈니스 규칙 |
| Q3 | 상품 필드 (name, desc, price, brandId) | Product 비즈니스 규칙 |
| Q4 | Stock 별도 엔티티 분리 | Product 비즈니스 규칙, 기능 흐름 |
| Q5 | Money VO (int) | Product 비즈니스 규칙 |
| Q6 | 고객/어드민 재고 표시 차이 | Product 기능 흐름 |
| Q7 | 좋아요 멱등성 | Like 기능 흐름, 비즈니스 규칙 |
| Q8 | likeCount 비정규화 | Like 비즈니스 규칙 |
| Q9 | 스냅샷 (상품명 + 가격 + 수량) | Order 비즈니스 규칙 |
| Q10 | Order + OrderItem 분리 | Order 비즈니스 규칙 |
| Q11 | OrderStatus enum 미래 확장 | Order 비즈니스 규칙 |
| Q12 | 서버 계산 totalAmount | Order 기능 흐름, 비즈니스 규칙 |
| Q13 | ArgumentResolver | 공통 인증 |
| Q14 | 브랜드 필드 (name + description) | Brand 비즈니스 규칙 |
| Q15 | Spring Pageable | 공통 페이지네이션 |
| Q16 | 삭제된 상품 좋아요 불가 + 목록 제외 | Like 기능 흐름, 비즈니스 규칙 |
| Q17 | 브랜드명 중복 불가 | Brand 기능 흐름, 비즈니스 규칙 |
| Q18 | 상품명 중복 허용 | Product 비즈니스 규칙 |
| Q19 | 재고 부족 전체 실패 | Order 기능 흐름, 비즈니스 규칙 |
| Q20 | 삭제 상품 포함 시 전체 실패 | Order 기능 흐름, 비즈니스 규칙 |
| Q21 | 주문 생성 시 재고 즉시 차감 | Order 기능 흐름, 비즈니스 규칙 |
| Q22 | 자기 자신 주문 제한 없음 | Order 비즈니스 규칙 |
| Q23 | 좋아요 목록 최신순 | Like 기능 흐름 |
| Q24 | 커스텀 sort 4종 | Product 정렬 옵션 |
| Q25 | 주문 조회 범위 (고객: 본인, 어드민: 전체) | Order 기능 흐름, 비즈니스 규칙 |
| Q26 | 수량 최소 1, 최대 재고 이하 | Order 비즈니스 규칙 |
| Q27 | 삭제된 브랜드에 상품 등록 불가 | Brand/Product 비즈니스 규칙 |
| Q28 | likeCount 음수 방지 | Like 비즈니스 규칙 |
| Q29 | 상품 수정 범위 (name, desc, price만) | Product 기능 흐름, 비즈니스 규칙 |
| Q30 | ~~재고 절대값 세팅~~ (구현 범위 제외) | - |
| Q31 | 브랜드명 변경 자동 반영 | Brand 기능 흐름, 비즈니스 규칙 |
| Q32 | 기존 ErrorType 4종 활용 | 공통 에러 처리 |

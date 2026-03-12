# 유비쿼터스 언어

프로젝트 전반에서 통일하여 사용하는 도메인 용어를 정의합니다.
코드, 문서, 커뮤니케이션에서 동일한 의미로 사용합니다.

---

## 1. Actor (행위자)

| 용어 | 설명 | 인증 방식 |
|------|------|-----------|
| **Customer** | 로그인한 일반 사용자. 상품 조회, 좋아요, 주문 가능 | `@LoginMember` (X-Loopers-LoginId + X-Loopers-LoginPw) |
| **Admin** | 관리자. 브랜드/상품/주문 관리 | `@AdminUser` (X-Loopers-Ldap) |

---

## 2. 카탈로그 BC (Brand + Product + Stock)

> 비즈니스 관심사: "판매할 상품 카탈로그를 관리한다"
> 브랜드 삭제 → 소속 상품 연쇄 soft delete가 하나의 트랜잭션으로 처리되므로 같은 BC에 속한다.

### Brand (어그리게이트)

| 용어 | 타입 | 설명 |
|------|------|------|
| **BrandModel** | Entity | 브랜드 엔티티. BaseEntity 상속. name(BrandName VO) + description |
| **BrandName** | @Embeddable VO | 브랜드명 값 객체. 유니크 제약, 빈값 불가, `value()` 접근자 |
| **BrandService** | Domain Service | 단일 도메인 로직. CRUD, 브랜드명 유니크 검증 |

### Product (어그리게이트)

| 용어 | 타입 | 설명 |
|------|------|------|
| **ProductModel** | Entity | 상품 엔티티. name, description, price(Money), brandId(ID 참조), likeCount(비정규화) |
| **Money** | @Embeddable VO | 금액 값 객체. int 내부 타입(원화), 음수 불가, 0원 허용. `add()`, `multiply()` 행위 메서드 |
| **StockModel** | Entity | 재고 엔티티. Product와 1:1 관계. `decrease()`, `increase()`, `hasEnough()` 행위 메서드 |
| **StockStatus** | 표시 상태 | 고객에게 보여주는 재고 상태. IN_STOCK(>10), LOW_STOCK(1~10), OUT_OF_STOCK(0) |
| **ProductService** | Domain Service | 상품 CRUD, likeCount 증감, soft delete |
| **StockService** | Domain Service | 재고 생성, 조회, 차감(`checkAndDecrease`) |
| **initialStock** | 요청 파라미터 | 상품 등록 시 초기 재고 수량 |

### Application Layer

| 용어 | 타입 | 설명 |
|------|------|------|
| **BrandFacade** | Application Facade | 유스케이스 조합. 삭제 시 소속 상품 연쇄 soft delete |
| **ProductFacade** | Application Facade | 상품 + Stock 동시 생성, 브랜드 존재 확인 |
| **브랜드 삭제 연쇄** | 비즈니스 규칙 | 브랜드 삭제 → 소속 상품 전체 soft delete. 하나의 트랜잭션 (Q1) |

---

## 3. 좋아요 BC (Like)

> 비즈니스 관심사: "고객의 상품 선호를 추적한다"

| 용어 | 타입 | 설명 |
|------|------|------|
| **LikeModel** | Entity | 좋아요 엔티티. userId + productId 유니크 제약 |
| **멱등성 (Idempotency)** | 비즈니스 규칙 | 좋아요 중복 등록 → 무시 + 200 OK. 취소 중복 → 무시 + 200 OK (Q7) |
| **likeCount 동기화** | 비즈니스 규칙 | 좋아요 추가 → `incrementLikeCount()`, 취소 → `decrementLikeCount()`. 음수 방지 가드 포함 (Q28) |
| **LikeService** | Domain Service | 좋아요 등록/취소, 존재 여부 조회, 목록 조회 |
| **LikeFacade** | Application Facade | 삭제된 상품 체크, 트랜잭션 내 likeCount 동기화 |

---

## 4. 주문 BC (Order)

> 비즈니스 관심사: "주문 이력을 기록하고 관리한다"

| 용어 | 타입 | 설명 |
|------|------|------|
| **OrderModel** | Entity | 주문 엔티티. userId, status(OrderStatus), totalAmount(Money) |
| **OrderItemModel** | Entity | 주문 상세 엔티티. orderId, productId, 스냅샷(productName, productPrice), quantity. `subtotal()` 행위 메서드 |
| **OrderStatus** | Enum | 주문 상태. CREATED(현재 사용) → CONFIRMED → SHIPPING → DELIVERED → CANCELLED (미래 확장용) |
| **스냅샷 (Snapshot)** | 비즈니스 개념 | 주문 시점의 상품명, 가격을 OrderItem에 복사 저장. 상품 삭제/변경 후에도 주문 내역 조회 가능 (Q9) |
| **All or Nothing** | 비즈니스 규칙 | 재고 부족 또는 삭제된 상품 포함 시 주문 전체 실패. 부분 성공 없음 (Q19) |
| **OrderService** | Domain Service | 주문 생성(총액 계산 포함), 조회 |
| **OrderFacade** | Application Facade | 상품 조회 → 재고 차감 → 주문 생성. 하나의 트랜잭션 |

---

## 5. 공통 패턴

### 6.1 엔티티 기반

| 용어 | 설명 |
|------|------|
| **BaseEntity** | 모든 엔티티의 부모 클래스. id, createdAt, updatedAt, deletedAt 자동 관리 |
| **Soft Delete** | `deletedAt`을 세팅하여 논리 삭제. `delete()` / `restore()` 메서드. 조회 시 `findByIdAndDeletedAtIsNull` |
| **guard()** | BaseEntity의 검증 메서드. `@PrePersist` / `@PreUpdate` 시 호출 |

### 6.2 값 객체 (Value Object)

| 용어 | 설명 |
|------|------|
| **@Embeddable VO** | JPA 임베딩 가능한 값 객체 패턴. 생성 시 검증, `value()` 접근자, equals/hashCode |
| **value()** | VO의 내부 값 접근 메서드. getter 대신 사용 (`loginId.value()`, `price.value()`) |

### 6.3 아키텍처 레이어

| 용어 | 설명 |
|------|------|
| **Controller** | HTTP 요청/응답 처리, 인증 어노테이션 적용. `interfaces/api/` 패키지 |
| **Facade** | 유스케이스 조합, `@Transactional` 경계 관리, 여러 Service 조합. `application/` 패키지 |
| **Service** | 단일 도메인 비즈니스 로직. `domain/` 패키지 |
| **Repository** | 데이터 접근 인터페이스(domain) + JPA 구현체(infrastructure) |

### 6.4 에러 처리

| 용어 | 설명 |
|------|------|
| **CoreException** | 비즈니스 예외. ErrorType enum 기반으로 생성 |
| **ErrorType** | 에러 유형 enum. NOT_FOUND, BAD_REQUEST, CONFLICT, INTERNAL_ERROR (Q32) |
| **ApiResponse** | 공통 응답 래퍼. `meta`(result, errorCode, message) + `data`(응답 본문) |

### 6.5 관계 설계

| 용어 | 설명 |
|------|------|
| **ID 참조** | JPA 연관관계(@ManyToOne 등) 없이 `Long brandId`, `Long productId`로만 참조 (ADR-008) |
| **1:1 분리** | Product-Stock 관계. 변경 이유가 다르므로 별도 엔티티/테이블 (ADR-001) |
| **비정규화** | Product.likeCount. 조회 성능을 위해 집계값을 저장. 쓰기 시 동기화 필요 (ADR-002) |

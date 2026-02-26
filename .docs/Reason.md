# 설계 결정 근거 (Design Decision Rationale)

본 문서는 Volume 3 구현 과정에서 내린 주요 설계 결정과 그 근거를 기록합니다.

---

## 1. 패키지 구조: Aggregate별 하위 패키지 채택

### 결정
`domain/model/` 아래 Aggregate별 하위 패키지로 분리 (`model/user/`, `model/product/`, `model/brand/`, `model/like/`, `model/order/`)

### 근거
- **process.md 원칙**: "패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태"
- **05-package-structure.md**: 평탄한 구조에 20~30개 파일이 쌓이면 탐색성과 응집도 저하
- Aggregate 경계가 패키지로 표현되어 `import`만으로 소속을 파악 가능
- 기존 레이어 구조(`application/`, `infrastructure/`, `interfaces/`)를 깨지 않음
- 변경 범위가 `import` 문 수정에 한정

### 기각한 대안
- `domain/` 자체를 Aggregate 단위로 분리 → 변경 범위가 너무 크고, 현재 규모(5 Aggregate)에 과도한 구조

---

## 2. Value Object 설계: Self-Validating + 정적 팩토리

### 결정
모든 VO는 `private` 생성자 + `of()` 정적 팩토리 메서드, 생성 시점에 검증 수행

### 근거
- **process.md 원칙**: "도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다"
- 기존 User 도메인의 `UserId.of()`, `Email.of()`, `Password.of()` 패턴과 일관성 유지
- Bean Validation 제거 후 도메인 계층 검증으로 통일 (커밋 `4c17f62`)
- 유효하지 않은 상태의 객체가 존재할 수 없음 → "항상 유효한 도메인 모델" 보장

### 적용 예시
```java
// Money.of(-1) → IllegalArgumentException
// Stock.of(-5) → IllegalArgumentException
// BrandName.of("") → IllegalArgumentException
```

---

## 3. 불변 도메인 객체: 상태 변경 시 새 인스턴스 반환

### 결정
모든 Aggregate Root와 Entity의 상태 변경 메서드는 새 객체를 반환 (기존 객체 불변)

### 근거
- 기존 User 도메인의 패턴 답습: `User.updatePassword()` → 새 `User` 반환
- 사이드 이펙트 방지: 한 참조를 수정해도 다른 참조에 영향 없음
- 테스트 용이성: 입력과 출력이 명확하여 단위 테스트 작성이 단순
- 동시성 안전: 불변 객체는 별도 동기화 없이 스레드 안전

### 적용 예시
```java
Product updated = product.decreaseStock(3);  // product는 변하지 않음
productRepository.save(updated);              // 새 인스턴스를 저장
```

---

## 4. Aggregate 간 ID 참조

### 결정
Aggregate 간에는 직접 참조 대신 ID(Long) 참조 사용. 단, 타입 안전한 식별자(`UserId`)는 해당 Aggregate 패키지에서 import

### 근거
- **03-class-diagram.md**: "Aggregate 간 ID 참조" 원칙
- **05-package-structure.md**: "UserId는 user/ 패키지에 그대로 둔다. 다른 Aggregate가 import해서 사용"
- Aggregate 간 결합도 최소화 → 각 Aggregate를 독립적으로 변경 가능
- JPA 레벨에서 Lazy Loading 이슈 원천 차단

### 적용
| 도메인 | 참조 방식 |
|--------|----------|
| `Product.brandId` | `Long` (Brand Aggregate와 느슨한 결합) |
| `Like.userId` | `UserId` (타입 안전한 ID 참조) |
| `Like.productId` | `Long` |
| `Order.userId` | `UserId` |
| `OrderItem.productId` | `Long` |

---

## 5. Soft Delete 패턴

### 결정
Brand, Product에 `deletedAt` 필드를 두어 논리적 삭제 수행

### 근거
- **01-requirements.md**: 상품/브랜드 삭제 시 기존 주문 데이터의 참조 무결성 유지 필요
- 물리적 삭제 시 주문 내역에서 "삭제된 상품" 표시 불가
- 조회 시 `isDeleted()` / `filter(p -> !p.isDeleted())` 로 간단히 필터링
- 향후 데이터 복구, 감사 로그 활용 가능

---

## 6. 비즈니스 로직 위치: 도메인 객체 vs Application Service

### 결정
단일 Aggregate 내 규칙은 도메인 객체에, 여러 Aggregate 협력은 Application Service에 배치

### 근거
- **process.md 원칙**: "규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다"
- **process.md 원칙**: "애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공"

### 구체적 배치

| 로직 | 위치 | 이유 |
|------|------|------|
| `Stock.decrease()` | Domain (VO) | 재고 차감은 Stock 자체의 규칙 |
| `Order.isCancellable()` | Domain (AR) | 상태 전이 규칙은 Order 자체의 불변식 |
| `Money.add/subtract` | Domain (VO) | 금액 연산은 Money 자체의 규칙 |
| Like 생성 + Product.likeCount 증가 | Application (LikeService) | 두 Aggregate(Like, Product) 협력 |
| 재고 차감 + 주문 생성 | Application (OrderService) | Product 재고차감 + Order 생성 협력 |
| Product + Brand 조합 조회 | Application (ProductQueryService) | 두 Aggregate 정보 조합 |

---

## 7. Command/Query Service 분리

### 결정
Product, Order 도메인은 Command Service와 Query Service를 분리

### 근거
- **03-class-diagram.md**: 설계 문서에서 CUD와 R 서비스를 분리 명시
- Command와 Query의 트랜잭션 특성이 다름 (`@Transactional` vs `@Transactional(readOnly = true)`)
- Query Service는 여러 Aggregate를 조합하여 읽기 전용 DTO를 반환 → Command와 관심사가 다름
- Brand, Like는 규모가 작아 통합 Service로 유지 (과도한 분리 방지)

| 도메인 | Command | Query | 분리 이유 |
|--------|---------|-------|----------|
| Brand | `BrandService` | (통합) | CRUD가 단순, 조합 조회 없음 |
| Product | `ProductService` | `ProductQueryService` | 상세 조회 시 Brand 정보 조합 필요 |
| Like | `LikeService` | (통합) | 조회 UseCase가 현재 없음 |
| Order | `OrderService` | `OrderQueryService` | 주문 생성(복잡한 트랜잭션) vs 조회(읽기 전용) |

---

## 8. UseCase 인터페이스 패턴

### 결정
각 유스케이스를 독립 인터페이스로 정의, Service가 필요한 UseCase를 구현

### 근거
- 기존 User 도메인의 `RegisterUseCase`, `AuthenticationUseCase` 패턴 답습
- **ISP (Interface Segregation Principle)**: Controller는 자신이 사용하는 UseCase만 의존
- DIP 준수: Interfaces 레이어 → Application 레이어의 인터페이스에 의존
- 테스트 시 필요한 UseCase만 Stub/Mock 가능

### 적용
```java
// Controller는 필요한 UseCase만 의존
public class ProductController {
    private final CreateProductUseCase createProductUseCase;
    private final ProductQueryUseCase productQueryUseCase;
    // DeleteProductUseCase는 주입받지 않음 → 불필요한 의존 제거
}
```

---

## 9. 주문 시점 가격 스냅샷

### 결정
`OrderItem.unitPrice`에 주문 시점의 상품 가격을 저장, `OrderSnapshot`에 상품명:가격 형태로 기록

### 근거
- **01-requirements.md**: 주문 시점의 가격이 보존되어야 함
- 상품 가격이 변경되어도 기존 주문의 결제 금액에 영향 없음
- 주문 상세 조회 시 주문 당시 가격 표시 가능
- **04-erd.md**: `order_items.unit_price` 컬럼으로 스냅샷 가격 저장

---

## 10. 에러 메시지: 도메인 객체 내부 배치

### 결정
각 도메인 객체의 검증 실패 메시지를 해당 객체 내부에 한국어로 직접 배치

### 근거
- **YAGNI 원칙**: 현재 다국어 지원 요구사항 없음, 에러 메시지 중앙화의 실익 없음
- 응집도: 검증 규칙과 에러 메시지가 같은 위치에 있어 수정 시 한 파일만 변경
- 기존 User 도메인 패턴 답습: `UserId`, `Email`, `Password` 등 모두 내부에 메시지 보유
- 향후 다국어/중앙화 필요 시 MessageSource 도입으로 마이그레이션 가능

---

## 11. Like 멱등성 (Idempotency)

### 결정
이미 좋아요한 상태에서 `like()` 호출 시 예외 대신 무시 (early return)

### 근거
- **01-requirements.md**: 중복 좋아요 방지
- 네트워크 재시도, 프론트엔드 더블클릭 등 실무에서 중복 호출 빈번
- 예외 발생 시 불필요한 에러 로그, 클라이언트 에러 핸들링 부담
- `unlike()` 도 동일하게 멱등적 처리: 좋아요하지 않은 상태에서 호출 시 무시

---

## 12. Order.create()에서 totalAmount 자동 계산

### 결정
`Order.create()` 내부에서 `OrderItem` 목록으로부터 `totalAmount`를 자동 계산

### 근거
- 외부에서 totalAmount를 전달받으면 조작/불일치 가능성 존재
- 도메인 불변식: `totalAmount = SUM(item.unitPrice * item.quantity)` 는 Order의 핵심 규칙
- `paymentAmount = totalAmount - discountAmount` 도 내부에서 계산하여 정합성 보장
- **process.md**: "도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다"

---

## 13. Admin/User Interceptor 분리

### 결정
`AuthenticationInterceptor`(User)와 `AdminAuthenticationInterceptor`(Admin)를 별도 컴포넌트로 구현

### 근거
- **01-requirements.md 2.2절**: Admin(`X-Loopers-Ldap`)과 User(`X-Loopers-LoginId` + `X-Loopers-LoginPw`)는 완전히 다른 인증 체계
- **06-admin-authentication.md**: Admin은 DB 조회 없이 헤더 값 일치만 확인, User 테이블 변경 불필요
- 단일 책임 원칙: 각 Interceptor가 하나의 인증 방식만 담당
- `WebMvcConfig`에서 경로 패턴으로 분리 등록: `/api/v1/**` → User, `/api-admin/v1/**` → Admin

---

## 14. Controller별 역할 분리 (Admin vs User)

### 결정
같은 도메인이라도 Admin Controller와 User Controller를 분리

### 근거
- **03-class-diagram Part E~H**: Brand, Product, Order 모두 Admin/User Controller 분리 설계
- Admin은 CRUD 전체 접근, User는 조회만 접근 → 하나의 Controller에 혼재 시 인증 경로 관리 복잡
- 엔드포인트 경로가 다름: `/api-admin/v1/brands` vs `/api/v1/brands`
- 각 Controller가 필요한 UseCase만 의존하여 결합도 최소화

| 도메인 | Admin Controller | User Controller |
|--------|-----------------|-----------------|
| Brand | `BrandAdminController` (CRUD) | `BrandController` (조회) |
| Product | `ProductAdminController` (CUD+조회) | `ProductController` (조회) |
| Like | - | `LikeController` (등록/취소) |
| Order | - | `OrderController` (생성/조회) |

---

## 15. Interfaces DTO와 Application DTO 분리

### 결정
Request/Response DTO를 `interfaces/api/dto/`에 별도 정의, Application 레이어의 record와 `from()` 메서드로 변환

### 근거
- **process.md**: "API request, response DTO와 응용 레이어의 DTO는 분리해 작성"
- Interfaces 레이어 변경(필드 추가/제거, 포맷 변경)이 Application 레이어에 전파되지 않음
- 기존 `UserInfoResponse.from(UserQueryUseCase.UserInfoResponse)` 패턴 답습
- `OrderCreateRequest.toCommand()`: DTO → Application Command 변환을 DTO 자체에 캡슐화

---

## 16. Infrastructure Layer: BaseEntity 미상속

### 결정
새로운 JPA Entity들이 `BaseEntity`를 상속하지 않고 자체 필드로 관리

### 근거
- 기존 `UserJpaEntity` 패턴 답습: 프로젝트 내 일관성 유지
- `BaseEntity`는 `ZonedDateTime` 사용, 도메인 모델은 `LocalDateTime` 사용 → 타입 불일치
- Like, OrderItem 등 `updated_at`/`deleted_at`가 불필요한 엔티티에 불필요한 컬럼 생성 방지
- 각 Entity가 자신에게 필요한 필드만 정확히 가짐 → 명시적이고 예측 가능

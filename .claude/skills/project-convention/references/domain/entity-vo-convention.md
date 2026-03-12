# 엔티티 / VO 설계 컨벤션

---

## 1. Entity 작성 규칙

### 기본 구조

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Embedded
    private Money totalPrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // === 생성 === //

    private Order(Long memberId, Money totalPrice, OrderStatus status) {
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public static Order create(Long memberId, int price) {
        return new Order(memberId, Money.of(price), OrderStatus.CREATED);
    }

    // === 도메인 로직 === //

    public void cancel() {
        validateCancellable();
        this.status = OrderStatus.CANCELLED;
    }

    // === 검증 === //

    private void validateCancellable() {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(OrderErrorCode.ALREADY_CANCELLED);
        }
    }
}
```

### 생성 패턴: 정적 팩토리 메서드 + private 생성자

정적 팩토리로 생성 의도를 표현하고, VO 변환/초기값/검증을 Entity가 통제하여 불변식을 보장한다.

```java
// private 생성자 + 정적 팩토리 메서드
private Order(Long memberId, Money totalPrice, OrderStatus status) {
    this.memberId = memberId;
    this.totalPrice = totalPrice;
    this.status = status;
}

public static Order create(Long memberId, int price) {
    return new Order(memberId, Money.of(price), OrderStatus.CREATED);
}

// ❌ 생성자 직접 노출
public Order(Long memberId, int price) { ... }

// ❌ @Builder
@Builder
public Order(Long memberId, int price) { ... }
```

### 순서 의존성이 있는 생성: 계산 먼저, 생성자에서 완결

Aggregate는 생성 시점부터 완전해야 한다. 계산을 생성자 호출 전에 끝내고, 생성자에 모든 값을 넘긴다.

```java
public static OrderModel create(Long userId, List<OrderItemModel> items,
                                int discountAmount) {
    validateUserId(userId);
    validateItems(items);
    int originalTotalPrice = OrderItemModel.calculateTotalPrice(items);
    OrderModel order = new OrderModel(
            userId, originalTotalPrice, discountAmount, OrderStatus.ORDERED);
    items.forEach(order::addItem); // JPA 연관관계 설정만 (비즈니스 상태 아님)
    return order;
}

private OrderModel(Long userId, int originalTotalPrice,
                   int discountAmount, OrderStatus status) {
    this.userId = userId;
    this.originalTotalPrice = originalTotalPrice;
    this.discountAmount = discountAmount;
    this.totalPrice = originalTotalPrice - discountAmount;
    this.status = status;
}
```

### 접근 제어

| 규칙 | 설정 |
|------|------|
| 기본 생성자 | `@NoArgsConstructor(access = PROTECTED)` — JPA 프록시 전용 |
| Setter | 도메인 메서드로 상태 변경 (`order.cancel()`) |
| Getter | `@Getter` 허용 — 읽기는 자유 |
| 필드 접근 | `private` — 직접 할당은 Entity 내부에서만 |

### Entity 내부 구조 순서

```java
@Entity
public class Order {
    // 1. 필드 (id, 일반 필드, VO, 연관관계)
    // 2. 정적 팩토리 메서드 (create, register ...)
    // 3. 도메인 로직 메서드 (cancel, changeStatus ...)
    // 4. private 검증 메서드 (validateXxx ...)
}
```

---

## 2. VO 설계 규칙

### 기본 원칙: VO를 만들지 않는다

검증과 행위는 Entity 도메인 메서드 또는 Domain Service에서 처리한다.

| 검증/행위 유형 | 처리 위치 |
|----------|----------|
| null/길이/범위 검증 | Entity `private static validateXxx` |
| 형식 규칙 (이메일, 비밀번호 정책) | Entity 도메인 메서드 또는 Domain Service |
| 외부 인프라 의존 (암호화 등) | Domain Service |
| 도메인 행위 (계산, 변환) | Entity 도메인 메서드 |

```java
@Entity
public class ProductModel extends BaseEntity {

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "stock", nullable = false)
    private int stock;

    public static ProductModel create(BrandModel brand, String name, int price, int stock) {
        validatePriceRange(price);
        validateStockRange(stock);
        return new ProductModel(brand, name, price, stock);
    }

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.stock -= quantity;
    }

    private static void validatePriceRange(int price) {
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
```

### VO / @Embeddable 결정 매트릭스

| 유형 | 조건 (모두 충족) | 구현 방식 | 예시 |
|------|-----------------|----------|------|
| **record VO** | 도메인 행위 2개 이상 + 여러 도메인 중복 + DB 저장 무관 | `record` (compact constructor에서 자기 검증) | `DateRange` |
| **@Embeddable** | 3개 이상 필드가 하나의 개념 + 함께 생성/조회 + 확장 가능성 | `@Embeddable` 클래스 (행위 없이 데이터 그룹핑만) | `ProductSnapshot` |
| **그 외** | 위 조건 미충족 | VO를 만들지 않음 — Entity/Domain Service에서 처리 | 대부분의 경우 |

**record VO 예시:**

```java
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start.isAfter(end)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "시작일이 종료일보다 늦을 수 없습니다.");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
```

**@Embeddable 규칙:**

- 행위 없이 데이터 그룹핑만 담당 (행위가 필요하면 Entity 메서드에서 처리)
- 같은 Entity에 같은 타입 2개 사용 금지 (`@AttributeOverride` 보일러플레이트 방지)
- 클래스명은 `{개념}Snapshot`, `{개념}Info` 등 역할이 드러나는 이름 사용

```java
@Embeddable
public class ProductSnapshot {

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "image_url")
    private String imageUrl;

    protected ProductSnapshot() {
    }

    public ProductSnapshot(String productName, String brandName, String imageUrl) {
        this.productName = productName;
        this.brandName = brandName;
        this.imageUrl = imageUrl;
    }
}
```

### 공유 VO 배치 규칙

| 아키텍처 | 배치 위치 |
|----------|----------|
| 레이어 우선 (현재) | `domain.common` 패키지 |
| 도메인 우선 | `common.domain` 패키지 |
| 멀티모듈 | `common-domain` 모듈 |

---

## 3. 검증 위치 규칙

| 검증 수준 | 위치 | 기준 |
|----------|------|------|
| **단일 값 / 크로스필드** | Entity `private static validateXxx` | 자기 필드만으로 판단 가능 |
| **외부 의존** | Domain Service | Repository 조회, 타 도메인 데이터, 인프라(암호화 등) 필요 |

**판단 규칙:**

- 자기 필드만으로 완결 → Entity `private static validateXxx`
- Repository 조회 필요 → Domain Service
- 타 도메인 정보 필요 → Domain Service
- 외부 인프라(암호화 등) 필요 → Domain Service

### 예시

```java
// 1. 단일 값 검증 → Entity 내부
@Entity
public class UserModel {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }
}

// 2. 외부 의존 검증 → Domain Service
public class UserService {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("...");

    public UserModel signup(String loginId, String rawPassword, ...) {
        validatePasswordFormat(rawPassword);          // 형식 검증도 Service에서 (암호화 전 raw 값 필요)
        validateBirthDateNotInPassword(rawPassword, birthDate); // 크로스필드 + 인프라 의존
        UserModel.create(loginId, passwordEncoder.encode(rawPassword), ...); // 암호화된 값 전달
    }
}
```

---

## 4. Entity vs Domain Service 로직 배치

핵심 기준: **"자기 상태(필드)만으로 완결되는가?"**

| 조건 | 배치 | 예시 |
|------|------|------|
| 자기 상태 변경/검증/계산 | Entity | `order.cancel()`, `order.calculateTotalPrice()` |
| 생성 로직 | Entity 정적 팩토리 | `Order.create(...)` |
| Repository 조회 필요 | Domain Service | `orderService.findOrThrow(id)` |
| 타 도메인 데이터 필요 | Domain Service | `orderService.create(memberData, productData)` |
| 여러 Entity 조율 | Domain Service | `orderService.transferOwnership(from, to)` |
| 외부 시스템 연동 | Domain Service | `orderService.requestPayment(order)` |

### Domain Service 추출 신호

Entity에 먼저 넣고, 아래 신호가 보이면 Domain Service로 추출한다.

| 신호 | 액션 |
|------|------|
| Entity 메서드가 20개 이상 | 관련 로직 묶어서 Domain Service로 추출 |
| Entity 테스트에 mock이 필요해짐 | 외부 의존이 있다는 뜻 → Domain Service로 |
| 같은 검증 로직이 여러 Entity에 중복 | Domain Service 또는 공통 VO로 추출 |

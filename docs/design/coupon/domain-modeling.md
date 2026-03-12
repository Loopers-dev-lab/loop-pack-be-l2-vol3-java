> Round 4 과제 — 쿠폰 도메인의 비즈니스 규칙을 요구사항 질문을 통해 도출하고 확정한 문서
>

---

## 📌 핵심 개념 구분

쿠폰 도메인은 **두 가지 핵심 개념**으로 나뉜다.

**쿠폰 템플릿 (Coupon)** — Admin이 생성하는 쿠폰의 원형. 할인 타입, 금액, 유효기간, 발급 수량 등을 정의한다.

**발급된 쿠폰 (OwnedCoupon)** — 유저가 템플릿으로부터 발급받아 소유하게 되는 개별 쿠폰. 상태(AVAILABLE/USED/EXPIRED)를 갖는다.

---

## ✅ 확정된 비즈니스 규칙

### 1. 쿠폰 템플릿 (Admin)

| # | 규칙 | 근거 |
| --- | --- | --- |
| R1 | 정액(FIXED) / 정률(RATE) 두 타입 존재 | PDF 요구사항 |
| R2 | totalQuantity로 총 발급 수량 제한 | Q1 결정 |
| R3 | 정률 쿠폰에 최대 할인 상한 없음 | Q5 결정 |
| R4 | 삭제 시 Soft Delete, 이미 발급된 쿠폰 정상 유지 | Q8 결정 |
| R5 | 핵심 조건(type, value, minOrderAmount) 수정 불가. name, expiredAt 등만 수정 가능 | Q9 결정 |
| R6 | minOrderAmount는 nullable (선택 조건) | PDF 요구사항 |

### 2. 쿠폰 발급

| # | 규칙 | 근거 |
| --- | --- | --- |
| R7 | 유저당 같은 템플릿에서 1번만 발급 가능 | 개념 정리 (이미지) |
| R8 | 유효기간 지난 템플릿은 발급 불가 | 개념 정리 (이미지) |
| R9 | 수량 소진된 템플릿은 발급 불가 | Q1 결정 |
| R10 | 발급 시 issuedQuantity 증가 → 동시성 제어 필요 | Q1 파생 |

### 3. 쿠폰 사용 (주문)

| # | 규칙 | 근거 |
| --- | --- | --- |
| R11 | 1주문에 1쿠폰만 적용 | PDF 요구사항 |
| R12 | 전체 주문 금액에 적용 (개별 상품 X) | 개념 정리 (이미지) |
| R13 | AVAILABLE 상태 + 유효기간 미경과만 사용 가능 | Q7 결정 |
| R14 | 본인 소유 쿠폰만 사용 가능 | PDF 요구사항 |
| R15 | 사용 후 즉시 USED로 변경, 재사용 불가 | PDF 요구사항 |
| R16 | 할인 금액은 주문 금액 초과 불가 (실 적용액 기록) | Q6 결정 |
| R17 | 스냅샷: 쿠폰 적용 전 금액, 할인 금액, 최종 결제 금액 포함 | PDF 요구사항 |
| R18 | 동일 쿠폰 동시 사용 → 동시성 제어 필요 | PDF 체크리스트 |

### 4. 쿠폰 복원 (주문 취소)

| # | 규칙 | 근거 |
| --- | --- | --- |
| R19 | 주문 취소 시 쿠폰 복원 | 개념 정리 (이미지) |
| R20 | 복원 시 만료 여부 체크 → AVAILABLE 또는 EXPIRED로 복원 | Q4 결정 |

### 5. 쿠폰 만료

| # | 규칙 | 근거 |
| --- | --- | --- |
| R21 | DB에 EXPIRED 상태 실제 저장 (AVAILABLE / USED / EXPIRED) | Q10 결정 |
| R22 | 배치 스케줄러로 주기적 만료 처리 (AVAILABLE → EXPIRED) | Q10 결정 |
| R23 | 사용/조회 시점에도 동적 검증 병행 | Q10 결정 |

### 6. 조회

| # | 규칙 | 근거 |
| --- | --- | --- |
| R24 | 내 쿠폰 목록: AVAILABLE / USED / EXPIRED 상태 함께 반환 | PDF 요구사항 |
| R25 | (향후) 다운로드 가능 목록: 이미 발급된 건 필터링 | Q2 결정 |
| R26 | (향후) 다운로드 가능 목록: 만료된 건 제외 | Q3 결정 |

---

## 🔍 질문을 통한 규칙 도출 과정

### Q1. 쿠폰 템플릿에 총 발급 수량 제한을 둘까?

**결정: totalQuantity + issuedQuantity로 수량 제한**

판단 근거: 개념 정리에서 "수량이 남아있는 쿠폰"이라는 조건을 이미 정의했고, 동시 발급 시 동시성 이슈가 생기므로 과제의 동시성 제어 요구사항과도 맞물림.

### Q2. 다운로드 가능한 쿠폰 목록에 이미 발급된 쿠폰도 보여줄까?

**결정: 이미 발급된 건 필터링해서 안 보여줌**

판단 근거: 유저가 받을 수 있는 것만 보여주는 게 깔끔. 이미 받은 건 "내 쿠폰 목록"에서 확인. 단, 현재 과제 스코프에는 이 API 자체가 없으므로 향후 추가 시 적용.

### Q3. 만료된 쿠폰은 다운로드 목록에서 제외?

**결정: 만료된 건 안 보여줌**

판단 근거: 개념 정리에서 "유효기간이 지나지 않은 쿠폰"이라는 조건으로 이미 정의됨.

### Q4. 주문 취소 시 쿠폰 유효기간이 이미 지났으면?

**결정: 만료되었으면 EXPIRED로, 아니면 AVAILABLE로 복원**

판단 근거: Q10에서 EXPIRED를 DB에 실제 저장하기로 했으므로, 복원 시 만료 여부를 체크해서 정확한 상태로 복원하는 것이 일관적.

### Q5. 정률 쿠폰에 최대 할인 금액 상한을 둘까?

**결정: 상한 없이 PDF 스펙 그대로**

판단 근거: PDF 요구사항에 maxDiscountAmount 필드가 없음. 과제 스펙에 충실하고, 필요하면 나중에 추가.

### Q6. 할인 적용 후 결제 금액이 0원 이하가 되면?

**결정: 할인액을 주문금액까지만 적용 (실 적용액 기록)**

판단 근거: 스냅샷에 "할인 10000원"이 아닌 실제 적용된 할인액을 기록해야 정합성이 맞음. `Math.min(discount, orderAmount)`로 조정.

### Q7. 쿠폰 사용 시점에 유효기간을 재검증하나?

**결정: 사용 시점에 유효기간 재검증 필수**

판단 근거: PDF에서 "사용 불가능한 쿠폰으로 요청 시 주문은 실패해야 합니다"라고 명시. 만료된 쿠폰은 사용 불가능한 쿠폰.

### Q8. Admin이 쿠폰 템플릿 삭제 시, 이미 발급된 쿠폰은?

**결정: Soft Delete, 발급된 쿠폰은 정상 유지**

판단 근거: IssuedCoupon이 Coupon을 FK로 참조하므로 hard delete 시 참조 무결성 깨짐. 이미 발급된 쿠폰은 유효기간까지 사용 가능해야 함.

### Q9. Admin이 쿠폰 템플릿 수정 시, 이미 발급된 쿠폰에 반영되나?

**결정: 핵심 조건(type, value, minOrderAmount) 수정 불가**

판단 근거: 수정 가능하게 하면 스냅샷 or 실시간 반영 고민이 생김. 핵심 조건을 못 바꾸게 하면 이 문제 자체가 사라짐. 수정하고 싶으면 새 템플릿 생성.

### Q10. EXPIRED 상태는 언제 어떻게 전이되나?

**결정: 배치 + 동적 검증 병행**

판단 근거: 배치로 주기적 정리하면 DB 상태가 정확해져서 쿼리가 단순해지고 어드민 조회/통계도 편해짐. 배치 주기 사이 불일치는 사용/조회 시점 동적 검증으로 보완. Q4의 복원 로직과도 일관성 있음.

---

## 🏗 엔티티 설계 (확정)

### 설계 결정 사항

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 발급 쿠폰 네이밍 | OwnedCoupon | 사용/복원 행위의 주어가 "소유자" |
| discountValue 타입 | Long + 검증 메서드 | 단순하게. 정률은 1~100 범위 검증 |
| 할인 타입 Enum | CouponDiscountType | "할인 방식"이라는 의미 명확. 향후 다른 차원의 타입과 구분 |
| 발급 시각 | createdAt으로 통일 | OwnedCoupon 생성 = 발급. 별도 issuedAt 불필요 |

### Coupon (Aggregate Root #1 — 템플릿)

```java
@Entity
public class Coupon {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private String name;

    @Enumerated(STRING)
    private CouponDiscountType discountType;  // FIXED / RATE

    private Long discountValue;               // 정액: 원, 정률: %

    private Long minOrderAmount;              // nullable

    private Integer totalQuantity;            // 총 발급 가능 수량
    private Integer issuedQuantity;           // 현재 발급 수량 (default 0)

    private LocalDateTime expiredAt;
    private boolean deleted;                  // soft delete (default false)
    private LocalDateTime createdAt;
}
```

**도메인 메서드:**

```java
public void validateIssuable() {
    if (deleted) throw 삭제된 쿠폰;
    if (expiredAt.isBefore(now())) throw 만료된 쿠폰;
    if (issuedQuantity >= totalQuantity) throw 수량 소진;
}

public void issue() {                        // 동시성 제어 대상
    validateIssuable();
    issuedQuantity++;
}

public long calculateDiscount(long orderAmount) {
    long discount = switch (discountType) {
        case FIXED -> discountValue;
        case RATE  -> orderAmount * discountValue / 100;
    };
    return Math.min(discount, orderAmount);   // R16: 주문 금액 초과 불가
}
```

### OwnedCoupon (Aggregate Root #2 — 소유 쿠폰)

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columns = {"coupon_id", "user_id"}))
public class OwnedCoupon {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Long couponId;                    // Aggregate 간 ID 참조
    private String couponName;               // 스냅샷 — 발급 시점 쿠폰명
    private CouponDiscountType discountType; // 스냅샷 — 할인 타입
    private Long discountValue;              // 스냅샷 — 할인 값
    private Long minOrderAmount;             // 스냅샷 — 최소 주문 금액 (nullable)
    private ZonedDateTime expiredAt;         // 스냅샷 — 만료 시각

    private Long userId;                      // 도메인 간 경계 — ID만
    private Long orderId;                     // 도메인 간 경계 — ID만 (nullable, 사용된 주문)

    @Enumerated(STRING)
    private OwnedCouponStatus status;         // AVAILABLE / USED / EXPIRED

    private LocalDateTime usedAt;             // nullable
    private LocalDateTime createdAt;          // = 발급 시각
}
```

**도메인 메서드:**

```java
public void validateUsable(Long requestUserId) {
    if (!userId.equals(requestUserId)) throw 본인 쿠폰 아님;   // R14
    if (status != AVAILABLE) throw 사용 불가 상태;              // R13
    if (expiredAt.isBefore(now())) throw 만료;                // R7 재검증 (자체 스냅샷)
}

public void validateMinOrderAmount(long orderAmount) {       // Coupon에서 이동
    if (minOrderAmount != null && orderAmount < minOrderAmount) throw 최소 주문 금액 미달;
}

public long calculateDiscount(long orderAmount) {            // Coupon에서 이동
    long discount = switch (discountType) {
        case FIXED -> discountValue;
        case RATE  -> orderAmount * discountValue / 100;
    };
    return Math.min(discount, orderAmount);
}

public void use(Long requestUserId, Long orderId) {  // 동시성 제어 대상
    validateUsable(requestUserId);
    status = USED;                            // R15
    usedAt = LocalDateTime.now();
    this.orderId = orderId;                   // 어떤 주문에서 사용되었는지 기록
}

public void restore() {                      // R19, R20
    if (status != USED) throw 복원 불가;
    if (expiredAt.isBefore(now())) {          // 자체 스냅샷으로 판단
        status = EXPIRED;
    } else {
        status = AVAILABLE;
    }
    usedAt = null;
    orderId = null;                           // 주문 참조 해제
}
```

### Enum

```java
public enum CouponDiscountType {
    FIXED,  // 정액 할인
    RATE    // 정률 할인 (%)
}

public enum OwnedCouponStatus {
    AVAILABLE,  // 사용 가능
    USED,       // 사용 완료
    EXPIRED     // 만료
}
```

### 동시성 제어 포인트

| 포인트 | 대상 필드 | 시나리오 |
| --- | --- | --- |
| 쿠폰 발급 | Coupon.issuedQuantity | 100명 동시 발급 → 수량 초과 방지 |
| 쿠폰 사용 | OwnedCoupon.status | 같은 쿠폰 동시 사용 → 중복 사용 방지 |

---

## 🧱 도메인 경계 결정

### 결정 1. 테이블 구조 → Coupon + OwnedCoupon (2테이블)

User(1) ↔ (N) OwnedCoupon (N) ↔ (1) Coupon — 다대다 관계이고, 중간 테이블에 자기만의 상태(AVAILABLE/USED/EXPIRED)가 있으므로 독립 엔티티로 분리 불가피. UNIQUE(couponId, userId)로 유저당 1개 보장.

네이밍은 **OwnedCoupon**으로 최종 결정. 사용/복원 행위의 주어가 "소유자"이므로 소유 관점이 적합.

### 결정 2. 쿠폰 도메인 위치 → 독립 도메인

자체 API 8개(Admin 6 + 유저 2), 자체 상태 머신, 동시성 제어 포인트 2개, 할인 계산 로직 — 분리를 정당화하는 충분한 복잡도.

### 결정 3. 도메인 간 경계와 의존 방향

**Aggregate 구조**

쿠폰 도메인 안에 Aggregate 2개. Coupon(템플릿)과 OwnedCoupon(소유 쿠폰)는 생명주기가 다르다 — Coupon이 soft delete 되어도 OwnedCoupon는 살아있어야 하므로 같은 Aggregate에 넣을 수 없다.

- Coupon — Aggregate Root #1 (템플릿)
- OwnedCoupon — Aggregate Root #2 (소유 쿠폰)

**참조 방향 규칙**

| 관계 | 방식 | 이유 |
| --- | --- | --- |
| OwnedCoupon → Coupon | Long couponId (ID만) + 스냅샷 필드 | 별도 Aggregate Root 간 ID 참조. 발급 시점 데이터 복사 |
| OwnedCoupon → User | Long userId (ID만) | 도메인 간 경계 |
| OwnedCoupon → Order | Long orderId (ID만, nullable) | 도메인 간 경계. 사용 이력은 쿠폰이 관리 |
| Order → OwnedCoupon | 참조 없음 | Order는 할인 결과(discountAmount)만 보유 |
| User → OwnedCoupon | 역참조 없음 | 쿠폰 도메인 서비스를 통해 접근 |

**Order의 할인 정보** — discountAmount, originalTotalPrice, totalPrice를 스냅샷으로 저장. 할인 출처(쿠폰/포인트 등)는 모름.

**향후 확장(브랜드별 쿠폰)** — Coupon에 brandId(nullable) 추가하는 정도로 대응 가능. 지금은 미구현.

### 결정 4. OwnedCoupon → Coupon 참조 방식: @ManyToOne → 스냅샷 분리

> 초기 설계에서는 "같은 도메인 내부이므로 `@ManyToOne` 직접 참조"로 결정했다. 구현 후 격벽 검증에서 이 결정을 재검토하고 스냅샷 분리로 변경했다.

#### 문제 발견: 격벽 구조 심층 분석

구현 완료 후, 각 연관관계가 격벽 원칙을 실제로 지키는지 하나씩 추적했다.

**1. OwnedCoupon → User: `Long userId` — 올바름**

OwnedCoupon은 User에 대해 소유자 확인(`equals`) 외에 아무것도 하지 않는다. User의 이름, 이메일 등 필드에 접근할 필요 없음. `coupon` 패키지가 `user` 패키지를 import하지 않으므로 격벽 유지.

**2. OwnedCoupon → Order: `Long orderId` — 올바름**

orderId는 사용 이력 추적 + 복원 시 조회 키 용도. Order의 상태, 금액 등에 접근할 필요 없음. `@ManyToOne Order`로 했다면 coupon 패키지가 order 패키지에 의존 → 격벽 파괴.

**3. Order → OwnedCoupon: 참조 없음 — 올바름**

Order는 `discountAmount`만 보유하고 할인 출처를 모른다. 취소 시 쿠폰 복원은 `couponService.restoreByOrderId(orderId)`로 처리하며, Order가 쿠폰 존재 여부를 판단하지 않는다.

**4. 도메인 간 조율: OrderFacade에서만 — 올바름**

```
OrderFacade
  ├── ProductService (product 도메인)
  ├── BrandService (brand 도메인)
  ├── CouponService (coupon 도메인)
  └── OrderService (order 도메인)
```

도메인 서비스 간 직접 호출 없이, Application Layer(Facade)에서만 조율. 순환 의존 없음.

**5. OwnedCoupon → Coupon: `@ManyToOne` 직접 참조 — 문제 발견**

OwnedCoupon이 자기 일을 하려면 **항상 Coupon 데이터가 필요**했다.

```java
// validateUsable — Coupon의 expiredAt 필요
this.coupon.getExpiredAt().isBefore(ZonedDateTime.now())

// restore — Coupon의 expiredAt 필요
this.coupon.getExpiredAt().isBefore(ZonedDateTime.now())

// Service 레이어 — Coupon의 minOrderAmount, discountType, discountValue 필요
coupon.validateMinOrderAmount(orderAmount);
coupon.calculateDiscount(orderAmount);
```

"별도 Aggregate Root"로 선언했지만, `@ManyToOne`으로 인해 OwnedCoupon은 Coupon 없이 아무것도 못 하는 구조였다. 이는 두 가지 문제를 만든다:

- **Aggregate 독립성 위반**: 생명주기가 다르다고 분리해놓고, 실제로는 Coupon에 런타임 종속
- **인프라 전략에 도메인 정합성 의존**: soft-delete여서 JPA lazy loading이 삭제된 Coupon도 로드할 수 있어 "동작"할 뿐. 도메인 규칙이 인프라 구현에 의존하는 것

#### 대안 비교

| | A. @ManyToOne 유지 | B. 스냅샷 분리 |
|---|---|---|
| Aggregate 독립성 | 위반 (Coupon 없이 동작 불가) | 완전 독립 |
| Coupon soft-delete 후 사용 | 동작함 (JPA lazy loading) | 동작함 (자체 데이터) |
| Coupon hard-delete | 깨짐 (FK 참조 실패) | 영향 없음 |
| Admin expiredAt 수정 | 이미 발급된 쿠폰에 즉시 반영 | 발급 시점 조건 확정 |
| 데이터 중복 | 없음 | 5개 필드 복사 |
| OwnedCoupon 자체 완결성 | 불완전 (검증/계산에 Coupon 필요) | 완전 (자기 데이터로 처리) |

#### 핵심 정책 질문: Admin이 만료일을 변경하면 이미 발급된 쿠폰은?

R5에 의해 `expiredAt`은 수정 가능한 필드다. `@ManyToOne`이면 수정 즉시 모든 OwnedCoupon에 반영되고, 스냅샷이면 발급 시점 조건이 유지된다.

**결정: 발급 시점 확정 (스냅샷 분리)**

판단 근거:
- 유저가 발급받을 때의 조건이 그 유저와의 "계약"이다. Admin이 사후에 조건을 바꿔도 이미 발급된 쿠폰에는 영향을 주지 않아야 한다
- Admin이 만료일을 단축했을 때 유저가 갑자기 쿠폰을 못 쓰게 되는 것은 사용자 경험상 부적절
- Aggregate Root 간 독립성 원칙과도 일치

#### 스냅샷 분리 후 변경 사항

**Before (v1 — @ManyToOne 직접 참조)**

```
OwnedCoupon {
    @ManyToOne Coupon coupon     ← Coupon에 런타임 종속
    Long userId
    Long orderId
    OwnedCouponStatus status
    ZonedDateTime usedAt
}
```

OwnedCoupon의 모든 검증/계산이 Coupon 객체를 경유해야 했다.

**After (v2 — 스냅샷 분리)**

```
OwnedCoupon {
    Long couponId                ← ID 참조만
    String couponName            ← 발급 시점 스냅샷
    CouponDiscountType discountType
    long discountValue
    Long minOrderAmount
    ZonedDateTime expiredAt
    Long userId
    Long orderId
    OwnedCouponStatus status
    ZonedDateTime usedAt
}
```

OwnedCoupon이 자기 데이터만으로 검증(`validateUsable`, `validateMinOrderAmount`)과 계산(`calculateDiscount`)을 수행한다. Coupon 엔티티에 대한 런타임 의존이 완전히 사라진다.

**도메인 메서드 이동:**

| 메서드 | Before | After | 이유 |
| --- | --- | --- | --- |
| `validateMinOrderAmount()` | CouponModel | OwnedCouponModel | 데이터 소유자로 이동 |
| `calculateDiscount()` | CouponModel | OwnedCouponModel | 데이터 소유자로 이동 |
| `validateUsable()` | `this.coupon.getExpiredAt()` | `this.expiredAt` | 자체 스냅샷 사용 |
| `restore()` | `this.coupon.getExpiredAt()` | `this.expiredAt` | 자체 스냅샷 사용 |

**CouponService 변경:**

```java
// Before — Coupon을 경유해서 검증/계산
OwnedCouponModel owned = findById(ownedCouponId);
CouponModel coupon = owned.getCoupon();          // ← Coupon 접근
coupon.validateMinOrderAmount(orderAmount);       // ← Coupon에 위임
owned.use(userId, orderId);
return coupon.calculateDiscount(orderAmount);     // ← Coupon에 위임

// After — OwnedCoupon이 자체 처리
OwnedCouponModel owned = findById(ownedCouponId);
owned.validateMinOrderAmount(orderAmount);        // ← OwnedCoupon 자체
owned.use(userId, orderId);
return owned.calculateDiscount(orderAmount);      // ← OwnedCoupon 자체
```

Service가 Coupon을 꺼내서 중개하던 역할이 사라지고, OwnedCoupon에게 직접 요청하는 구조가 된다.

#### 판단 원칙 (이 사례에서 도출)

> "같은 도메인이라도, 별도 Aggregate Root로 분리했다면 참조 방식은 ID 참조가 원칙이다. `@ManyToOne` 직접 참조는 Aggregate Root 간 생명주기 독립성을 훼손한다."

> "B가 A의 데이터를 검증/계산에 사용하고, A는 B와 독립적으로 변경될 수 있다면, B는 생성 시점에 필요한 데이터를 스냅샷으로 복사해야 한다."

---

## 💭 도메인 경계 의사결정 과정

### 전제: 경계를 긋기 위한 3가지 질문

도메인 경계를 결정할 때 아래 순서로 질문을 던졌다.

1. **Q1. 이 도메인은 어떤 기능을 제공하고, 복잡도는?** → 현재 복잡도가 독립 도메인으로의 분리를 정당화하는지 판단
2. **Q2. Aggregate Root는 누구인가?** → 도메인 위계(누가 누구를 소유하는가)로 결정. null 허용 X, 생명주기 일치는 암묵적 약속
3. **Q3. Root가 결정되면, 접근 방향은?** → Root를 통해서만 접근, 역참조는 원칙적으로 불필요

---

### Q1. 쿠폰 도메인은 어떤 기능을 제공하고, 복잡도는?

쿠폰 도메인이 제공하는 기능을 나열했다.

- Admin 템플릿 CRUD (API 6개)
- 유저 대고객 API: 발급, 내 쿠폰 조회 (API 2개)
- 선착순 발급: 수량 제한 + 동시성 제어
- 상태 머신: AVAILABLE → USED, AVAILABLE → EXPIRED, USED → AVAILABLE/EXPIRED
- 할인 계산 로직: 정액/정률
- 배치 만료 처리

동시성 제어 포인트가 2개(발급 시 issuedQuantity 증가, 사용 시 status 변경), 자체 API가 8개, 자체 상태 머신이 존재한다.

**판단:** 이 정도 복잡도면 독립 도메인으로 분리하는 것이 정당하다. 주문의 하위에 두기엔 쿠폰 자체의 라이프사이클과 책임이 너무 크다.

---

### Q2. Aggregate Root는 누구인가?

쿠폰 도메인 안에 Coupon(템플릿)과 OwnedCoupon(소유 쿠폰) 두 개의 엔티티가 있다. 이 둘을 같은 Aggregate에 넣을 수 있는지 판단하기 위해 생명주기를 비교했다.

**생명주기 비교:**

- Coupon 삭제(soft delete) → OwnedCoupon는 살아있어야 한다 (비즈니스 규칙 Q8에서 확정: "Admin이 쿠폰 템플릿 삭제해도 이미 발급된 쿠폰은 정상 유지")
- Coupon 없이 OwnedCoupon 생성 → 불가능 (couponId NOT NULL)
- OwnedCoupon 없이 Coupon 존재 → 가능 (아무도 발급받지 않은 쿠폰)

Coupon이 죽어도 OwnedCoupon는 살아야 한다. 생명주기가 불일치하므로 같은 Aggregate에 넣을 수 없다.

**결론:** Coupon과 OwnedCoupon는 각각 별도의 Aggregate Root. 같은 쿠폰 도메인 안에 Aggregate가 2개 존재하는 구조.

---

### Q3-1. 유저와 쿠폰의 경계는?

OwnedCoupon는 couponId(NOT NULL)와 userId(NOT NULL) 모두를 가진다. 양쪽 없이는 존재할 수 없는데, 어느 도메인에 속하는가?

비즈니스 책임으로 판단했다.

| 행위 | 책임 주체 |
| --- | --- |
| 발급 가능 여부 판단 (수량, 유효기간, 중복) | 쿠폰 도메인 |
| 상태 전이 (AVAILABLE → USED → EXPIRED) | 쿠폰 도메인 |
| 할인 금액 계산 | 쿠폰 도메인 |
| 만료 배치 처리 | 쿠폰 도메인 |
| "내 쿠폰 목록 조회" | 쿠폰 도메인 (userId를 조건으로) |

User 도메인은 OwnedCoupon에 대해 아무 책임이 없다. OwnedCoupon의 모든 비즈니스 로직은 쿠폰 도메인에서 처리된다.

**결론:**

- OwnedCoupon는 쿠폰 도메인에 속한다
- OwnedCoupon → User: `Long userId`(ID만 참조). @ManyToOne으로 User 엔티티를 직접 참조하지 않는다
- User → OwnedCoupon: 역참조 없음. "내 쿠폰 목록"이 필요하면 쿠폰 도메인의 서비스(`CouponService.getMyCoupons(userId)`)를 통해 접근한다
- User 도메인은 OwnedCoupon의 존재를 모른다

### Q3-2. 주문과 쿠폰의 경계는?

주문 시 쿠폰을 "사용"하는 흐름이 있지만, 주문 도메인과 쿠폰 도메인은 서로를 직접 참조하지 않는다.

**조율 방식:** OrderFacade(Application Layer)에서 CouponService를 호출하여 쿠폰 검증 → 사용 처리를 수행한다. 도메인 레벨에서 Order는 Coupon/OwnedCoupon 엔티티를 직접 알지 못한다.

**Order가 보관하는 쿠폰 정보 — 할인 결과만 저장:**

- discountAmount — 실 할인 적용 금액
- originalTotalPrice — 쿠폰 적용 전 금액
- totalPrice — 최종 결제 금액

**결론:**

- Order → OwnedCoupon: 참조 없음. Order는 할인 결과(discountAmount)만 보유
- OwnedCoupon → Order: `Long orderId`(ID만 참조, nullable). 쿠폰이 어떤 주문에서 사용되었는지 자기가 기록
- 주문과 쿠폰의 연결은 Application Layer(OrderFacade)에서 조율

### Q3-2 회고: 관계 방향 수정 (v2)

> 초기 설계에서는 `Order.ownedCouponId`로 Order가 쿠폰을 참조했다. 구현 후 리뷰에서 관계 방향이 잘못되었음을 발견하고 수정했다.

**Before (v1 — 잘못된 방향)**

```
Order { ownedCouponId (nullable), discountAmount }   ← Order가 쿠폰을 알아야 함
OwnedCoupon { status, usedAt }                       ← 어디서 쓰였는지 모름
```

문제점:
1. **Order가 쿠폰 도메인에 의존** — Order 엔티티가 쿠폰 관련 필드(ownedCouponId)를 갖고, 취소 시 쿠폰 복원 여부를 Order가 판단해야 함
2. **nullable FK 코드 스멜** — 대부분의 주문에서 null인 필드가 항상 존재
3. **관심사 불일치** — Order에게 할인은 "결과(discountAmount)"가 중요하지 "출처(ownedCouponId)"가 중요한 게 아님
4. **쿠폰 사용 이력의 주인 불명확** — OwnedCoupon은 USED 상태인데 어디서 쓰였는지 모름. 감사 추적이 Order에 분산됨
5. **확장성 제약** — 할인 출처가 쿠폰 외에 포인트, 프로모션으로 확장되면 Order에 FK가 계속 추가됨

**After (v2 — 수정된 방향)**

```
Order { discountAmount }                              ← 할인 결과만 보유, 쿠폰 무관
OwnedCoupon { status, usedAt, orderId (nullable) }   ← 어디서 쓰였는지 자기가 앎
```

개선 효과:
1. **Order가 쿠폰 도메인을 모름** — discountAmount만 가지고, 할인이 쿠폰인지 포인트인지 관심 없음
2. **nullable FK 제거** — Order에서 nullable 참조 제거
3. **쿠폰 사용 이력 일원화** — OwnedCoupon이 status + usedAt + orderId를 모두 관리
4. **취소 흐름 단순화** — `couponService.restoreByOrderId(orderId)` 호출. Order가 쿠폰 존재 여부를 판단할 필요 없음
5. **확장 용이** — 포인트, 프로모션 등 다른 할인 수단이 추가되어도 Order는 변경 없음

**관계 방향 판단 원칙 (이 사례에서 도출):**

> "A가 B의 처리 결과만 필요하고, B의 상태나 이력까지 알 필요가 없다면, A는 결과만 스냅샷하고 B가 A를 참조하는 것이 올바른 방향이다."

이 원칙을 적용하면:
- Order는 할인 결과(discountAmount)만 필요 → Order에 결과만 저장
- OwnedCoupon은 자신이 어디서 사용되었는지 알아야 함 → OwnedCoupon에 orderId 저장

---

### 그 외 의사결정에서 던졌던 질문들

#### "테이블을 하나로 할 수 없는가?"

처음에는 Coupon 테이블 하나로 모든 걸 처리할 수 있는지 검토했다. 유저-쿠폰 관계를 분석한 결과:

- 유저는 서로 다른 여러 쿠폰을 가질 수 있다
- 하나의 쿠폰 템플릿은 여러 유저에게 발급될 수 있다
- 유저당 같은 쿠폰은 1개만 가질 수 있다

다대다(M:N) 관계이고, 중간 테이블이 자기만의 상태(status, usedAt, issuedAt)를 가진다. 상태를 가지는 중간 테이블은 단순 조인 테이블이 아니라 독립 엔티티다. 2테이블 분리는 불가피했다.

#### "같은 도메인 내부라도 Aggregate Root 간 참조는 ID 참조로 해야 하는가?"

초기에는 "같은 도메인인가, 다른 도메인인가"로 참조 방식을 구분했다.

- **같은 도메인 내부** → `@ManyToOne` 직접 참조 (v1)
- **도메인 간** → ID만 참조

그러나 격벽 검증 과정에서 **Aggregate Root 간 생명주기 독립성**이 더 중요한 기준임을 발견했다.

결정 4에서 분석한 대로, OwnedCoupon과 Coupon은 같은 도메인이지만 별도 Aggregate Root이며 생명주기가 다르다. `@ManyToOne`은 이 독립성을 훼손한다.

**수정된 기준 (v2):**

- **같은 Aggregate 내부** (Order ↔ OrderItem): 양방향 매핑 허용. 생명주기가 일치하므로 객체 참조가 자연스러움
- **Aggregate Root 간** (OwnedCoupon → Coupon): 같은 도메인이라도 ID 참조 + 필요 시 스냅샷. 생명주기가 다르므로 독립성 보장
- **도메인 간** (OwnedCoupon → User, Order): ID만 참조. 도메인 간 결합도를 낮추기 위함

#### "Q4와 Q10의 결정이 서로 충돌했다"

Q4에서 "주문 취소 시 만료된 쿠폰은 EXPIRED로 복원"이라고 결정했다. 이후 Q10에서 "EXPIRED는 조회 시 동적 판단만(DB에 안 씀)"으로 결정했는데, Q4와 충돌했다. DB에 EXPIRED를 안 쓰는데 복원 시에만 EXPIRED를 쓸 수는 없다.

해소: Q10을 "배치 + 동적 검증 병행(DB에 EXPIRED 실제 저장)"으로 변경. 그 결과:

- Q4의 복원 로직이 일관적으로 동작 (만료 체크 후 EXPIRED 또는 AVAILABLE로 복원)
- 내 쿠폰 목록 조회 시 DB status 컬럼만 읽으면 되므로 쿼리가 단순해짐
- 배치 주기 사이 불일치는 사용/조회 시점 동적 검증으로 보완

#### "Coupon과 OwnedCoupon을 별도 패키지로 분리해야 하는가?"

OwnedCoupon이 Aggregate Root로 승격되고 스냅샷 분리까지 완료되면서, 두 엔티티가 실질적으로 독립적인 모습을 갖추게 되었다. 이 시점에서 `domain/coupon/` 패키지를 `domain/coupon/`과 `domain/ownedcoupon/`으로 물리적 분리할지 검토했다.

**결정: 분리하지 않는다. 하나의 Bounded Context에 두 Aggregate Root를 유지한다.**

판단 근거:

1. **같은 Bounded Context다** — OwnedCoupon은 Coupon 없이는 존재 의미가 없다. "쿠폰 발급"이라는 유즈케이스가 둘을 반드시 함께 다룬다 (`CouponService.issue()`에서 Coupon 재고 차감 + OwnedCoupon 생성). Aggregate Root가 2개라는 것이 Bounded Context가 2개라는 뜻은 아니다.

2. **분리하면 인위적 의존이 생긴다** — `ownedcoupon` 패키지가 `coupon` 패키지의 `CouponModel`, `CouponDiscountType`, `CouponErrorCode`를 import해야 한다. 순환 의존은 아니지만 한쪽이 다른쪽에 완전히 의존하는 패키지 분리는 인위적이다. 공유 enum(`CouponDiscountType`)을 어디에 둘지 같은 부수적 고민만 늘어난다.

3. **현재 규모에서 과설계다** — `domain/coupon/` 아래 파일이 9개. 패키지 분리로 얻는 가독성 이점이 없다. 수십 개가 될 때 재검토해도 늦지 않다.

**그렇다면 한 패키지 안에서 벽을 어떻게 관리하는가?**

패키지로 물리적 벽을 치지 않아도, **Aggregate Root 경계 자체가 논리적 벽**이다. 현재 구조가 이미 취하고 있는 격리 전략:

| 격리 원칙 | 적용 상태 |
| --- | --- |
| JPA 연관관계 없음 | `Long couponId` ID 참조만 사용 |
| 스냅샷으로 자급자족 | OwnedCoupon이 자체 필드로 검증/계산 |
| 독립 Repository | `CouponRepository` / `OwnedCouponRepository` 분리 |
| 생성 시점에만 접점 | `OwnedCouponModel.create(CouponModel, userId)` 한 곳뿐 |
| 파일 네이밍으로 그루핑 | `OwnedCoupon*` 접두사로 자연스럽게 묶임 |

이 격리가 유지되는 한, 물리적 패키지 분리 없이도 두 Aggregate Root의 독립성이 보장된다.

**판단 원칙 (이 사례에서 도출):**

> "Aggregate Root가 독립적이라고 해서 반드시 패키지를 분리해야 하는 것은 아니다. 패키지 분리의 기준은 Bounded Context이고, Aggregate Root 간 격리의 기준은 참조 방식(ID 참조 + 스냅샷)과 Repository 분리다."

#### "브랜드별 쿠폰 확장은 지금 구현하는가?"

개념 정리 단계에서 "나중에 특정 브랜드에 속한 상품에 대한 할인이 들어갈 수 있다"는 요건이 있었다. 지금 구현하지는 않되, 구조가 이 확장을 막지 않는지 확인했다.

쿠폰이 독립 도메인이므로, 향후 Coupon에 `brandId(nullable)` 필드를 추가하고 할인 계산 시 브랜드 한정 체크 로직만 넣으면 된다. Order 도메인은 건드릴 필요가 없다. 쿠폰을 주문의 하위 도메인으로 뒀다면 이 확장 시 주문 도메인까지 수정해야 했을 것이다.

#### "발급 쿠폰 네이밍 — OwnedCoupon으로 확정"

네이밍 후보 3가지를 검토했다.

- **CouponIssue**: PDF API 경로(`/issues`)와 일치. 그러나 "issue"가 행위(동사)에 가까워서 엔티티 이름으로 어색할 수 있음
- **IssuedCoupon**: "발급된 쿠폰"이라는 상태를 가진 객체 느낌. `issuedCoupon.use()`가 자연스러움
- **OwnedCoupon**: 유저의 소유 관계를 강조. "소유한 쿠폰을 사용한다"

최종 결정: **OwnedCoupon**. 발급 이후의 주요 행위(사용, 복원, 만료)가 전부 "소유자 관점"이므로 소유 관계를 강조하는 네이밍이 적합했다. `ownedCoupon.use(userId)`, `ownedCoupon.restore()` 등 코드에서도 자연스럽게 읽힌다.

---

## 📐 다음 단계 플랜

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1단계 | 요구사항 수집 (PDF + 이미지) | ✅ 완료 |
| 2단계 | 요구사항에 질문 던지기 (Q1~Q10) | ✅ 완료 |
| 3단계 | 핵심 비즈니스 규칙 확정 (R1~R26) | ✅ 완료 |
| 도메인 경계 | 테이블 구조 / 도메인 위치 / 의존 방향 결정 | ✅ 완료 |
| 4단계 | 네이밍 최종 결정 → OwnedCoupon | ✅ 완료 |
| 5단계 | 엔티티 설계 (Coupon, OwnedCoupon, Enum, 도메인 메서드) | ✅ 완료 |
| 6단계 | Repository + Service 계층 | ✅ 완료 |
| 7단계 | 주문 흐름 통합 (OrderFacade) | ✅ 완료 |
| 8단계 | 동시성 제어 및 테스트 | ✅ 완료 |

---

## 🗃️ 6단계: Repository + Service 계층 설계

### 구현 참고 — BaseEntity 적용

설계 문서의 엔티티 코드는 개념 수준의 의사코드다. 실제 구현 시 아래 프로젝트 컨벤션을 적용한다.

| 설계 문서 (의사코드) | 실제 구현 | 이유 |
| --- | --- | --- |
| `private boolean deleted` | `BaseEntity.deletedAt` 사용 | BaseEntity가 soft delete 관리 |
| `private LocalDateTime createdAt` | `BaseEntity.createdAt` (ZonedDateTime) | BaseEntity가 관리 |
| `private LocalDateTime expiredAt` | `private ZonedDateTime expiredAt` | 프로젝트 시간 타입 통일 |
| `if (deleted)` | `if (getDeletedAt() != null)` | BaseEntity 방식 |
| `LocalDateTime.now()` | `ZonedDateTime.now()` | 프로젝트 시간 타입 통일 |
| 클래스명 `Coupon` | `CouponModel` | 프로젝트 엔티티 네이밍 컨벤션 |
| 클래스명 `OwnedCoupon` | `OwnedCouponModel` | 프로젝트 엔티티 네이밍 컨벤션 |

### 스냅샷 분리에 따른 도메인 메서드 배치

스냅샷 분리(결정 4)로 인해 `validateMinOrderAmount`와 `calculateDiscount`는 OwnedCouponModel에 위치한다. OwnedCoupon이 스냅샷 데이터를 자체 보유하므로 데이터 소유자가 직접 처리하는 것이 자연스럽다.

CouponModel에는 `calculateDiscount`, `validateMinOrderAmount`가 남아 있지 않다. Coupon의 책임은 **템플릿 관리**(생성, 수정, 삭제, 발급 가능 여부 검증, 발급 수량 증가)로 한정된다.

### CouponErrorCode

```java
@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_001", "쿠폰을 찾을 수 없습니다."),
    ALREADY_DELETED(HttpStatus.BAD_REQUEST, "COUPON_002", "이미 삭제된 쿠폰입니다."),
    ALREADY_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_003", "만료된 쿠폰입니다."),
    QUANTITY_EXHAUSTED(HttpStatus.BAD_REQUEST, "COUPON_004", "쿠폰 수량이 소진되었습니다."),
    ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_005", "이미 발급받은 쿠폰입니다."),
    OWNED_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_006", "소유 쿠폰을 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "COUPON_007", "본인의 쿠폰만 사용할 수 있습니다."),
    NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_008", "사용 가능한 상태가 아닙니다."),
    NOT_RESTORABLE(HttpStatus.BAD_REQUEST, "COUPON_009", "복원할 수 없는 쿠폰입니다."),
    MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_010", "최소 주문 금액을 충족하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### Repository 인터페이스

3-클래스 패턴 (domain 인터페이스 → infrastructure JpaRepository + RepositoryImpl)을 따른다.

#### CouponRepository (domain)

```java
public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    Optional<CouponModel> findByIdWithLock(Long id);   // 비관적 락 (발급 시)
    Page<CouponModel> findAll(Pageable pageable);
}
```

#### OwnedCouponRepository (domain)

```java
public interface OwnedCouponRepository {
    OwnedCouponModel save(OwnedCouponModel ownedCoupon);
    Optional<OwnedCouponModel> findById(Long id);
    Optional<OwnedCouponModel> findByIdWithLock(Long id);  // 비관적 락 (사용 시)
    Optional<OwnedCouponModel> findByOrderId(Long orderId);  // 주문 취소 시 쿠폰 복원용
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);  // R7 중복 발급 체크
    List<OwnedCouponModel> findAllByUserId(Long userId);            // R24 내 쿠폰 목록
}
```

### CouponCommand (Domain DTO)

```java
public class CouponCommand {
    public record Create(
        String name,
        CouponDiscountType discountType,
        Long discountValue,
        Long minOrderAmount,       // nullable
        Integer totalQuantity,
        ZonedDateTime expiredAt
    ) {}
}
```

- `Create`는 파라미터 6개 → Command DTO 사용
- `update(Long id, String name, ZonedDateTime expiredAt)`는 파라미터 3개 → 원시 타입 직접 전달

### CouponService (Domain Service)

하나의 Service가 Coupon과 OwnedCoupon 두 Repository를 관리한다. 같은 쿠폰 도메인이므로 분리하지 않는다.

```java
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;

    // === 템플릿 관리 (Admin) === //

    @Transactional
    public CouponModel register(CouponCommand.Create command) { ... }

    @Transactional(readOnly = true)
    public CouponModel getById(Long id) { ... }

    @Transactional(readOnly = true)
    public Page<CouponModel> getAll(Pageable pageable) { ... }

    @Transactional
    public void update(Long id, String name, ZonedDateTime expiredAt) { ... }

    @Transactional
    public void delete(Long id) { ... }

    // === 발급 (Customer) === //

    @Transactional
    public OwnedCouponModel issue(Long couponId, Long userId) { ... }

    // === 사용 (주문 연동) === //

    @Transactional
    public long useAndCalculateDiscount(Long ownedCouponId, Long userId, Long orderId, long orderAmount) { ... }

    // === 복원 (주문 취소 연동) === //

    @Transactional
    public void restoreByOrderId(Long orderId) { ... }

    // === 조회 (Customer) === //

    @Transactional(readOnly = true)
    public List<OwnedCouponModel> getMyOwnedCoupons(Long userId) { ... }
}
```

#### 메서드별 흐름

**register** — 쿠폰 템플릿 생성

```
1. CouponModel.create(command 필드들)  ← Entity 정적 팩토리 (내부 검증)
2. couponRepository.save(coupon)
3. return coupon
```

**update** — 수정 가능 필드만 변경 (R5)

```
1. coupon = getById(id)
2. coupon.update(name, expiredAt)     ← Entity 메서드 (핵심 조건 수정 불가)
```

**delete** — Soft Delete (R4)

```
1. coupon = getById(id)
2. coupon.delete()                    ← BaseEntity.delete()
```

**issue** — 쿠폰 발급 (동시성 제어 대상)

```
1. coupon = couponRepository.findByIdWithLock(couponId)   ← 비관적 락
2. 중복 발급 체크: existsByCouponIdAndUserId(couponId, userId)  ← R7
3. coupon.issue()                                         ← validateIssuable + issuedQuantity++
4. ownedCouponRepository.save(OwnedCouponModel.create(coupon, userId))  ← 발급 시점 스냅샷 복사
5. return ownedCoupon
```

**useAndCalculateDiscount** — 쿠폰 사용 + 할인 계산 (동시성 제어 대상)

```
1. ownedCoupon = ownedCouponRepository.findByIdWithLock(ownedCouponId)  ← 비관적 락
2. ownedCoupon.validateMinOrderAmount(orderAmount)   ← R6 (자체 스냅샷)
3. ownedCoupon.use(userId, orderId)                  ← validateUsable + USED 전이 + orderId 기록 (R13~R15)
4. return ownedCoupon.calculateDiscount(orderAmount) ← R16 할인 금액 계산 (자체 스냅샷)
```

**restoreByOrderId** — 주문 취소 시 쿠폰 복원 (R19, R20)

```
1. ownedCoupon = ownedCouponRepository.findByOrderId(orderId)
2. if present → ownedCoupon.restore()   ← 만료 여부에 따라 AVAILABLE 또는 EXPIRED, orderId = null
```

**getMyOwnedCoupons** — 내 쿠폰 목록 (R24)

```
1. return ownedCouponRepository.findAllByUserId(userId)
```

### CouponFacade (Application Service)

Admin과 Customer 모두 하나의 Facade에서 처리한다 (기존 OrderFacade 패턴과 동일).

```java
@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    // === Admin === //

    @Transactional
    public CouponResult.Detail registerCoupon(CouponCriteria.Create criteria) { ... }

    @Transactional(readOnly = true)
    public CouponResult.Detail getCoupon(Long id) { ... }

    @Transactional(readOnly = true)
    public Page<CouponResult.Summary> getAllCoupons(Pageable pageable) { ... }

    @Transactional
    public void updateCoupon(Long id, CouponCriteria.Update criteria) { ... }

    @Transactional
    public void deleteCoupon(Long id) { ... }

    // === Customer === //

    @Transactional
    public void issueCoupon(Long couponId, Long userId) { ... }

    @Transactional(readOnly = true)
    public List<CouponResult.OwnedCouponDetail> getMyOwnedCoupons(Long userId) { ... }
}
```

Facade의 역할은 CouponService 위임 + DTO 변환뿐이다. 비즈니스 로직은 없다.

---

## 🔗 7단계: 주문 흐름 통합

### OrderModel 변경사항

기존 필드와의 관계:

| 필드 | 의미 | 변경 |
| --- | --- | --- |
| `originalTotalPrice` | 할인 적용 전 주문 금액 (상품 합계) | 기존 유지 |
| `discountAmount` | 할인 금액 | **신규 추가** (default 0) |
| `totalPrice` | 최종 결제 금액 (originalTotalPrice - discountAmount) | 기존 유지, 계산 로직 수정 |

```java
// OrderModel에 추가되는 필드
@Column(name = "discount_amount", nullable = false)
private int discountAmount;              // default 0
```

> **ownedCouponId는 Order에 두지 않는다.** Order는 할인 결과만 알면 되고, 할인 출처(쿠폰)는 OwnedCoupon이 orderId로 관리한다. (Q3-2 회고 참조)

### OwnedCouponModel 변경사항

| 필드 | 의미 | 변경 |
| --- | --- | --- |
| `orderId` | 이 쿠폰이 사용된 주문 ID (nullable) | **신규 추가** |

```java
// OwnedCouponModel에 추가되는 필드
@Column(name = "order_id")
private Long orderId;                    // nullable — 사용 전에는 null
```

### OrderModel.create 수정

기존 `create(Long userId, List<OrderItemModel> items)` 시그니처를 변경하여 할인 결과를 받는다.

```java
// 할인 없는 주문 (기존 호환)
public static OrderModel create(Long userId, List<OrderItemModel> items) {
    return create(userId, items, 0);
}

// 할인 적용 주문
public static OrderModel create(Long userId, List<OrderItemModel> items,
                                int discountAmount) {
    validateUserId(userId);
    validateItems(items);
    OrderModel order = new OrderModel(userId, 0, OrderStatus.ORDERED);
    items.forEach(order::addItem);
    int calculatedPrice = order.calculateTotalPrice();
    order.originalTotalPrice = calculatedPrice;
    order.discountAmount = discountAmount;
    order.totalPrice = Math.max(0, calculatedPrice - discountAmount);
    return order;
}
```

### recalculateTotalPrice 수정

```java
public void recalculateTotalPrice() {
    int activeItemsTotal = items.stream()
            .filter(item -> item.getStatus() == OrderItemStatus.ORDERED)
            .mapToInt(item -> item.getOrderPrice() * item.getQuantity())
            .sum();
    this.totalPrice = Math.max(0, activeItemsTotal - this.discountAmount);
}
```

부분 취소 시 할인 금액은 스냅샷으로 유지한다 (R17). 활성 상품 합계에서 할인을 뺀 금액이 최종 결제 금액이 된다.

### CouponService 변경사항

```java
// 사용 — OwnedCoupon 자체 스냅샷으로 검증/계산
@Transactional
public long useAndCalculateDiscount(Long ownedCouponId, Long userId, Long orderId, long orderAmount) {
    ownedCoupon = ownedCouponRepository.findByIdWithLock(ownedCouponId);
    ownedCoupon.validateMinOrderAmount(orderAmount);  // 자체 스냅샷
    ownedCoupon.use(userId, orderId);
    return ownedCoupon.calculateDiscount(orderAmount); // 자체 스냅샷
}

// 복원 — orderId로 해당 주문에 사용된 쿠폰을 찾아서 복원
@Transactional
public void restoreByOrderId(Long orderId) {
    ownedCouponRepository.findByOrderId(orderId)
        .ifPresent(OwnedCouponModel::restore);  // 쿠폰 없는 주문이면 no-op
}
```

### OwnedCouponRepository 변경사항

```java
public interface OwnedCouponRepository {
    // ... 기존 메서드 ...
    Optional<OwnedCouponModel> findByOrderId(Long orderId);  // 주문 취소 시 쿠폰 복원용
}
```

### OrderFacade.createOrder 수정

```
쿠폰 추가 흐름:
1. 재고 검증 + 차감
2. 브랜드명 조회
3. OrderItemModel 생성
4. 주문 생성 (discountAmount = 0) — orderId 확보
5. ownedCouponId가 있으면:
   a. couponService.useAndCalculateDiscount(ownedCouponId, userId, order.id, originalTotalPrice)
   b. order.applyDiscount(discountAmount)
```

```java
@Transactional
public OrderResult.OrderSummary createOrder(Long userId, OrderCriteria.Create criteria) {
    // 1~3. 기존 재고 차감 + 브랜드 조회 + OrderItem 생성 (기존과 동일)
    List<ProductInfo.StockDeduction> deductionInfos = productService.validateAndDeductStock(...);
    Map<Long, String> brandNameMap = brandService.getNameMapByIds(...);
    List<OrderItemModel> items = deductionInfos.stream()
            .map(info -> OrderItemModel.create(...))
            .toList();

    // 4. 주문 먼저 생성 (orderId 확보)
    OrderModel order = orderService.createOrder(userId, items, 0);

    // 5. 쿠폰 처리
    if (criteria.ownedCouponId() != null) {
        int discountAmount = (int) couponService.useAndCalculateDiscount(
                criteria.ownedCouponId(), userId, order.getId(),
                OrderItemModel.calculateTotalPrice(items));
        order.applyDiscount(discountAmount);
    }

    return OrderResult.OrderSummary.from(order);
}
```

> Order를 먼저 생성하여 orderId를 확보한 뒤, 쿠폰 사용 시 orderId를 전달한다. 쿠폰 검증 실패 시 트랜잭션 롤백으로 Order도 함께 취소된다.

### OrderFacade — 주문 취소 시 쿠폰 복원

```java
@Transactional
public void cancelMyOrderItem(Long userId, Long orderId, Long orderItemId) {
    OrderModel order = orderService.getByIdAndUserId(orderId, userId);
    orderService.cancelItem(orderId, orderItemId);

    // 주문 전체 취소 시 쿠폰 복원 (R19)
    if (order.getStatus() == OrderStatus.CANCELLED) {
        couponService.restoreByOrderId(orderId);  // 쿠폰 없으면 no-op
    }

    // 재고 복구 (기존)
    OrderItemModel cancelledItem = ...;
    productService.increaseStock(cancelledItem.getProductId(), cancelledItem.getQuantity());
}
```

쿠폰 복원 조건:
- `order.getStatus() == CANCELLED` — 모든 아이템이 취소되어 주문 자체가 취소된 경우에만
- Order가 쿠폰 존재 여부를 **판단하지 않음** — `restoreByOrderId`가 해당 orderId로 OwnedCoupon을 찾아 복원. 없으면 no-op

부분 취소 시에는 쿠폰을 복원하지 않는다. 할인은 주문 전체에 적용된 것이므로 (R12).

### OrderCriteria.Create 수정

```java
public class OrderCriteria {
    public record Create(
        List<CreateItem> items,
        Long ownedCouponId          // nullable — 신규 추가
    ) {
        public record CreateItem(Long productId, int quantity, int expectedPrice) {}
    }
}
```

> `ownedCouponId`는 Criteria(요청 DTO)에만 존재한다. Order 엔티티에는 들어가지 않는다.

### 통합 시퀀스 — 쿠폰 적용 주문 생성

```
Client → OrderController → OrderFacade
  │
  ├─ ProductService.validateAndDeductStock()    [재고 검증 + 차감]
  ├─ BrandService.getNameMapByIds()             [브랜드명 조회]
  ├─ OrderItemModel 생성
  │
  ├─ OrderService.createOrder(userId, items, 0)  [주문 먼저 생성 — orderId 확보]
  │
  ├─ if (ownedCouponId != null)
  │   └─ CouponService.useAndCalculateDiscount()
  │       ├─ OwnedCoupon 비관적 락 획득
  │       ├─ OwnedCoupon.validateMinOrderAmount()  [R6, 자체 스냅샷]
  │       ├─ OwnedCoupon.use(userId, orderId)      [R13~R15, orderId 기록]
  │       └─ return OwnedCoupon.calculateDiscount() [R16, 자체 스냅샷]
  │   └─ order.applyDiscount(discountAmount)
  │
  └─ return OrderResult.OrderSummary.from(order)
```

### 통합 시퀀스 — 주문 취소 시 쿠폰 복원

```
Client → OrderController → OrderFacade
  │
  ├─ OrderService.getByIdAndUserId()            [소유자 검증]
  ├─ OrderService.cancelItem()                  [아이템 취소 → 전체 취소 여부 판단]
  │
  ├─ if (order.status == CANCELLED)
  │   └─ CouponService.restoreByOrderId(orderId)
  │       └─ OwnedCoupon 조회 (orderId) → 있으면 restore()
  │           └─ R19, R20: 만료 여부에 따라 AVAILABLE/EXPIRED, orderId = null
  │
  └─ ProductService.increaseStock()             [재고 복구]
```

---

## 🔒 8단계: 동시성 제어

### 전략 선택: 비관적 락 (Pessimistic Lock)

| 대안 | 장점 | 단점 | 적합도 |
| --- | --- | --- | --- |
| **비관적 락** | 구현 단순, 충돌 시 즉시 차단, 재시도 불필요 | 락 대기 시간, 데드락 가능성 | ✅ 채택 |
| 낙관적 락 | 락 없이 읽기, 충돌 시 재시도 | 재시도 로직 필요, 높은 경합에서 비효율 | ❌ |
| Redis 분산 락 | 분산 환경 대응, DB 부하 분산 | 인프라 복잡도, Redis 장애 시 정합성 | 🔜 향후 확장 |

채택 근거:
- 쿠폰 발급은 선착순(높은 경합) → 낙관적 락은 재시도 폭주 위험
- 현재 단일 DB 구조 → 분산 락 불필요
- 비관적 락이 가장 단순하고 확실한 정합성 보장

### 락 적용 포인트

| 포인트 | 대상 | 락 획득 위치 | 락 해제 |
| --- | --- | --- | --- |
| 쿠폰 발급 | `Coupon` row | `CouponService.issue()` → `findByIdWithLock` | 트랜잭션 커밋 시 |
| 쿠폰 사용 | `OwnedCoupon` row | `CouponService.useAndCalculateDiscount()` → `findByIdWithLock` | 트랜잭션 커밋 시 |

### 구현 방식 — JpaRepository

```java
// CouponJpaRepository
public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponModel> findByIdWithLock(@Param("id") Long id);

    Page<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);
}

// OwnedCouponJpaRepository
public interface OwnedCouponJpaRepository extends JpaRepository<OwnedCouponModel, Long> {

    Optional<OwnedCouponModel> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oc FROM OwnedCouponModel oc WHERE oc.id = :id")
    Optional<OwnedCouponModel> findByIdWithLock(@Param("id") Long id);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    List<OwnedCouponModel> findAllByUserId(Long userId);
}
```

### 데드락 방지

- 락 획득 순서 고정: 한 트랜잭션 내에서 Coupon → OwnedCoupon 순서로만 락을 잡는다
- 발급(`issue`)은 Coupon만 락, 사용(`useAndCalculateDiscount`)은 OwnedCoupon만 락 — 교차 없음
- 복원(`restore`)은 락 없이 진행 — 주문 취소 흐름에서만 호출되며, 같은 OwnedCoupon의 동시 복원은 발생하지 않음 (주문이 이미 CANCELLED 상태여야 하므로)

### 유니크 제약으로 이중 안전장치 (R7)

비관적 락으로 발급 수량을 제어하되, DB 유니크 제약 `UNIQUE(coupon_id, user_id)`이 최종 안전장치 역할을 한다. 락 + 유니크 제약의 이중 보호로 동시 발급 시에도 유저당 1개 보장.

### 동시성 테스트 시나리오

| # | 시나리오 | 기대 결과 |
| --- | --- | --- |
| C1 | 100명이 동시에 totalQuantity=50인 쿠폰 발급 | 정확히 50명만 발급 성공, 50명 실패 |
| C2 | 같은 유저가 2개 기기에서 동시에 같은 쿠폰 사용 | 1건만 성공, 1건 실패 |
| C3 | 같은 유저가 동시에 같은 쿠폰 발급 시도 | 1건만 성공, 1건 실패 (UNIQUE 제약) |

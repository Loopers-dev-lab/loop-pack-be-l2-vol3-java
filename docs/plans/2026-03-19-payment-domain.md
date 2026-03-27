# Payment Domain Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 주문에 대한 순수 결제 도메인을 구현한다. 장애 대응(CircuitBreaker, Retry, Fallback)은 이 단계에서 제외하고, 결제 엔티티 + PG 클라이언트 + 결제 API + 콜백 수신까지를 범위로 한다.

**Architecture:** Payment를 Order와 별도 엔티티로 분리하여 1주문:N결제 구조를 지원한다. PG 호출은 트랜잭션 밖에서 수행하여 DB 커넥션 점유를 방지한다. 콜백 수신 시 결제 상태를 업데이트하고 주문 상태를 연동한다.

**Tech Stack:** Java 21, Spring Boot 3.4.4, JPA, RestTemplate, MySQL 8.0, JUnit 5, TestContainers

---

## 미결사항 결정 및 근거

### 1. Order-Payment 관계: **단순 ID 참조** (선택)

| 선택지 | 장점 | 단점 |
|--------|------|------|
| JPA `@ManyToOne` | 객체 그래프 탐색 편리, JOIN FETCH 가능 | 양방향 참조 시 순환 위험, 생명주기가 다른 엔티티 간 강결합 |
| **단순 ID 참조** | 느슨한 결합, Aggregate 경계 명확, 기존 프로젝트 패턴과 일치 | 조회 시 별도 쿼리 필요 |

**근거:** 프로젝트 전체가 ID 참조 패턴을 따르고 있다(OrderItemModel.orderId, OrderModel.couponIssueId 등). Payment와 Order는 생명주기가 다르다(주문 1건에 결제 N번 시도 가능). Aggregate 경계를 존중하는 것이 이 프로젝트의 설계 철학에 부합한다.

### 2. OrderStatus 확장: **PAYMENT_PENDING, PAYMENT_FAILED 추가** (선택)

| 선택지 | 장점 | 단점 |
|--------|------|------|
| 기존 유지 (Payment 상태로만 관리) | OrderStatus 변경 없음, 영향 범위 최소 | 주문 상태만 보고는 결제 진행 여부를 알 수 없음 |
| **OrderStatus 확장** | 주문 상태만으로 전체 흐름 파악 가능, 조회 쿼리 단순 | OrderStatus 변경에 따른 기존 코드 영향 검토 필요 |

**근거:** 사용자가 "내 주문 목록"을 조회할 때, 주문 상태만으로 "결제 대기 중"인지 알 수 있어야 한다. Payment 테이블을 매번 JOIN하는 것보다 OrderStatus에 결제 흐름을 반영하는 것이 직관적이다. 기존 테스트에서 `CREATED` 상태만 검증하므로 하위 호환성 문제 없다.

**상태 전이:**
```
CREATED → PAYMENT_PENDING → CONFIRMED → SHIPPING → DELIVERED
                          → PAYMENT_FAILED → (재시도 가능)
                                           → CANCELLED
```

### 3. 결제 금액: **Order의 finalAmount 사용** (선택)

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **Order의 finalAmount** | 단일 진실 공급원, 금액 불일치 방지 | 주문-결제 간 의존성 |
| Request에서 받기 | 유연함 | 프론트와 서버의 금액 불일치 가능성, 검증 로직 추가 필요 |

**근거:** 결제 금액은 주문의 최종 금액(할인 적용 후)이어야 한다. 클라이언트에서 금액을 보내면 조작 가능성이 있고, 서버에서 재계산하면 Order에서 가져오는 것과 동일하다. 따라서 Order.finalAmount를 신뢰 기반으로 사용한다.

### 4. 카드번호 저장: **마스킹 저장** (선택)

| 선택지 | 장점 | 단점 |
|--------|------|------|
| 전체 저장 | 구현 단순 | PCI-DSS 위반, 보안 리스크 |
| **마스킹 저장** | 사용자에게 "어떤 카드로 결제했는지" 표시 가능, 보안 확보 | 마스킹 로직 필요 |
| 미저장 | 보안 최상 | 결제 내역에서 카드 정보 확인 불가 |

**근거:** 실무에서는 PCI-DSS 때문에 전체 저장이 불가하다. 하지만 "1234-****-****-1451" 형태로 앞4자리+뒤4자리를 보여주는 것은 일반적인 UX이다. PG에 보낼 때는 전체 번호를 전송하되, DB에는 마스킹된 값만 저장한다.

### 5. HTTP 클라이언트: **RestTemplate** (선택)

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **RestTemplate** | 과제 명시, 학습 비용 낮음, 별도 의존성 없음 | 유지보수 모드 (deprecated 방향) |
| RestClient | 현대적 API, Spring 6.1+ | 과제 명시와 다소 벗어남 |
| FeignClient | 선언적, 깔끔 | spring-cloud 의존성 추가 필요 |

**근거:** 과제 요구사항에 "RestTemplate 혹은 FeignClient"로 명시되어 있다. RestTemplate이 가장 단순하고 별도 의존성이 필요 없다. PG 호출은 결제 요청 + 상태 확인 2~3개 엔드포인트뿐이므로 FeignClient의 선언적 장점이 크지 않다.

### 6. pg-simulator 확보: **별도 결정** (이 Plan 범위 밖)

pg-simulator는 별도 앱으로 실행하는 것이 전제이다. 이 Plan에서는 commerce-api 내부 코드만 다루며, PG 클라이언트의 통합 테스트에서는 MockRestServiceServer를 활용한다.

### 7. 콜백 엔드포인트: **1단계에 포함** (선택)

**근거:** 비동기 결제의 핵심은 "요청 → 콜백 수신 → 상태 업데이트"이다. 콜백 없이 결제 도메인을 만들면 PENDING 상태에서 영원히 멈추는 반쪽짜리 구현이 된다. 순수 결제 흐름의 완결성을 위해 콜백 수신까지 포함한다. 단, 콜백 미수신 시 폴링/복구 로직은 2~3단계로 미룬다.

---

## 구현 범위 & 패키지 구조

```
com.loopers/
├── domain/payment/
│   ├── PaymentModel.java          # 결제 엔티티
│   ├── PaymentStatus.java         # PENDING | SUCCESS | FAILED | CANCELLED
│   ├── CardType.java              # SAMSUNG | KB | HYUNDAI
│   └── PaymentRepository.java     # Repository 인터페이스
├── domain/order/
│   └── OrderStatus.java           # PAYMENT_PENDING, PAYMENT_FAILED 추가
│   └── OrderModel.java            # 상태 전이 메서드 추가
├── application/payment/
│   ├── PaymentService.java        # 결제 도메인 서비스
│   ├── PaymentFacade.java         # 결제 유스케이스 (주문 조회 + PG 호출 + 상태 관리)
│   ├── PaymentInfo.java           # Application DTO
│   └── PaymentCommand.java        # 결제 요청 Command
├── infrastructure/payment/
│   ├── PaymentRepositoryImpl.java # Repository 구현체
│   ├── PaymentJpaRepository.java  # JPA Repository
│   ├── PgClient.java              # PG HTTP 클라이언트
│   └── dto/
│       ├── PgPaymentRequest.java  # PG 요청 DTO
│       └── PgPaymentResponse.java # PG 응답 DTO
├── interfaces/api/payment/
│   ├── PaymentV1Controller.java   # 결제 API
│   ├── PaymentV1ApiSpec.java      # Swagger 인터페이스
│   └── PaymentV1Dto.java          # Request/Response DTO
└── config/
    └── PgClientConfig.java        # RestTemplate Bean + PG 설정
```

**application.yml 추가:**
```yaml
pg:
  base-url: http://localhost:8082
  callback-url: http://localhost:8080/api/v1/payments/callback
  timeout:
    connect: 1000
    read: 3000
```

---

## Task 1: PaymentStatus Enum + CardType Enum

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentStatus.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/CardType.java`

**Step 1: PaymentStatus 작성**

```java
package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

**Step 2: CardType 작성**

```java
package com.loopers.domain.payment;

public enum CardType {
    SAMSUNG,
    KB,
    HYUNDAI
}
```

**Step 3: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentStatus.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/CardType.java
git commit -m "feat: PaymentStatus, CardType Enum 추가"
```

---

## Task 2: PaymentModel 엔티티 (Red → Green)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentModelTest.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentModel.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.loopers.domain.payment;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    @DisplayName("결제 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면 상태가 PENDING이다")
        @Test
        void createsWithPendingStatus() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            CardType cardType = CardType.SAMSUNG;
            String maskedCardNo = "1234-****-****-1451";
            Money amount = new Money(50000);

            // when
            PaymentModel payment = new PaymentModel(orderId, userId, cardType, maskedCardNo, amount);

            // then
            assertAll(
                () -> assertThat(payment.orderId()).isEqualTo(orderId),
                () -> assertThat(payment.userId()).isEqualTo(userId),
                () -> assertThat(payment.cardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.maskedCardNo()).isEqualTo(maskedCardNo),
                () -> assertThat(payment.amount()).isEqualTo(amount),
                () -> assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.transactionKey()).isNull(),
                () -> assertThat(payment.failureReason()).isNull()
            );
        }

        @DisplayName("orderId가 null이면 예외가 발생한다")
        @Test
        void throwsWhenOrderIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(null, 1L, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId가 null이면 예외가 발생한다")
        @Test
        void throwsWhenUserIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, null, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 null이면 예외가 발생한다")
        @Test
        void throwsWhenCardTypeNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, null, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("maskedCardNo가 blank이면 예외가 발생한다")
        @Test
        void throwsWhenCardNoBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "  ", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 null이면 예외가 발생한다")
        @Test
        void throwsWhenAmountNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234-****-****-1451", null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 전이")
    @Nested
    class StatusTransition {

        private PaymentModel createPayment() {
            return new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000));
        }

        @DisplayName("transactionKey를 설정할 수 있다")
        @Test
        void assignsTransactionKey() {
            PaymentModel payment = createPayment();
            payment.assignTransactionKey("20250816:TR:9577c5");
            assertThat(payment.transactionKey()).isEqualTo("20250816:TR:9577c5");
        }

        @DisplayName("PENDING → SUCCESS 전이가 가능하다")
        @Test
        void transitionsToSuccess() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("PENDING → FAILED 전이가 가능하다")
        @Test
        void transitionsToFailed() {
            PaymentModel payment = createPayment();
            payment.markFailed("한도 초과");
            assertAll(
                () -> assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.failureReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("SUCCESS 상태에서 다시 SUCCESS로 전이해도 멱등하다")
        @Test
        void idempotentSuccess() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            payment.markSuccess(); // 중복 콜백 대응
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("SUCCESS 상태에서 FAILED로 전이하면 예외가 발생한다")
        @Test
        void throwsWhenSuccessToFailed() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            assertThrows(CoreException.class, () -> payment.markFailed("오류"));
        }

        @DisplayName("FAILED 상태에서 SUCCESS로 전이하면 예외가 발생한다")
        @Test
        void throwsWhenFailedToSuccess() {
            PaymentModel payment = createPayment();
            payment.markFailed("한도 초과");
            assertThrows(CoreException.class, () -> payment.markSuccess());
        }
    }
}
```

**Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "PaymentModelTest" -x compileTestJava
```
Expected: FAIL (컴파일 에러 — PaymentModel 클래스 없음)

**Step 3: PaymentModel 구현**

```java
package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "masked_card_no", nullable = false)
    private String maskedCardNo;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long userId, CardType cardType, String maskedCardNo, Money amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.maskedCardNo = maskedCardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        guard();
    }

    @Override
    protected void guard() {
        if (orderId == null) throw new CoreException(ErrorType.BAD_REQUEST, "주문 정보는 필수입니다.");
        if (userId == null) throw new CoreException(ErrorType.BAD_REQUEST, "사용자 정보는 필수입니다.");
        if (cardType == null) throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        if (maskedCardNo == null || maskedCardNo.isBlank()) throw new CoreException(ErrorType.BAD_REQUEST, "카드번호는 필수입니다.");
        if (amount == null) throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
    }

    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void markSuccess() {
        if (this.status == PaymentStatus.SUCCESS) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 성공 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 실패 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public Long orderId() { return orderId; }
    public Long userId() { return userId; }
    public CardType cardType() { return cardType; }
    public String maskedCardNo() { return maskedCardNo; }
    public Money amount() { return amount; }
    public PaymentStatus status() { return status; }
    public String transactionKey() { return transactionKey; }
    public String failureReason() { return failureReason; }
}
```

**Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "PaymentModelTest"
```
Expected: ALL PASS

**Step 5: 커밋**

```bash
git add apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentModelTest.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentModel.java
git commit -m "feat: PaymentModel 엔티티 구현 (TDD)"
```

---

## Task 3: OrderStatus 확장 + OrderModel 상태 전이 (Red → Green)

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderStatus.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderModel.java`
- Modify: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderModelTest.java`

**Step 1: OrderModelTest에 상태 전이 테스트 추가**

```java
// OrderModelTest.java에 추가할 Nested 클래스
@DisplayName("결제 상태 전이")
@Nested
class PaymentStatusTransition {

    @DisplayName("CREATED → PAYMENT_PENDING 전이가 가능하다")
    @Test
    void transitionsToPaymentPending() {
        OrderModel order = new OrderModel(1L, new Money(10000), Money.ZERO, null);
        order.startPayment();
        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @DisplayName("PAYMENT_PENDING → CONFIRMED 전이가 가능하다 (결제 성공)")
    @Test
    void transitionsToConfirmed() {
        OrderModel order = new OrderModel(1L, new Money(10000), Money.ZERO, null);
        order.startPayment();
        order.confirmPayment();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @DisplayName("PAYMENT_PENDING → PAYMENT_FAILED 전이가 가능하다")
    @Test
    void transitionsToPaymentFailed() {
        OrderModel order = new OrderModel(1L, new Money(10000), Money.ZERO, null);
        order.startPayment();
        order.failPayment();
        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @DisplayName("PAYMENT_FAILED → PAYMENT_PENDING 전이가 가능하다 (재결제)")
    @Test
    void retriesPaymentFromFailed() {
        OrderModel order = new OrderModel(1L, new Money(10000), Money.ZERO, null);
        order.startPayment();
        order.failPayment();
        order.startPayment(); // 재시도
        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @DisplayName("CONFIRMED 상태에서 startPayment를 호출하면 예외가 발생한다")
    @Test
    void throwsWhenAlreadyConfirmed() {
        OrderModel order = new OrderModel(1L, new Money(10000), Money.ZERO, null);
        order.startPayment();
        order.confirmPayment();
        assertThrows(CoreException.class, () -> order.startPayment());
    }
}
```

**Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "OrderModelTest"
```
Expected: FAIL (컴파일 에러 — PAYMENT_PENDING 없음, startPayment 메서드 없음)

**Step 3: OrderStatus에 값 추가**

```java
package com.loopers.domain.order;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    CANCELLED
}
```

**Step 4: OrderModel에 상태 전이 메서드 추가**

OrderModel.java의 `validateOwner` 메서드 아래에 추가:

```java
public void startPayment() {
    if (this.status != OrderStatus.CREATED && this.status != OrderStatus.PAYMENT_FAILED) {
        throw new CoreException(ErrorType.BAD_REQUEST, "결제를 시작할 수 없는 주문 상태입니다.");
    }
    this.status = OrderStatus.PAYMENT_PENDING;
}

public void confirmPayment() {
    if (this.status != OrderStatus.PAYMENT_PENDING) {
        throw new CoreException(ErrorType.BAD_REQUEST, "결제 확인을 처리할 수 없는 주문 상태입니다.");
    }
    this.status = OrderStatus.CONFIRMED;
}

public void failPayment() {
    if (this.status != OrderStatus.PAYMENT_PENDING) {
        throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패를 처리할 수 없는 주문 상태입니다.");
    }
    this.status = OrderStatus.PAYMENT_FAILED;
}
```

**Step 5: 테스트 실행 → 전체 통과 확인**

```bash
./gradlew test --tests "OrderModelTest"
```
Expected: ALL PASS

**Step 6: 기존 Order 관련 테스트도 통과하는지 확인**

```bash
./gradlew test --tests "*Order*"
```
Expected: ALL PASS (기존 테스트는 CREATED 상태만 검증하므로 영향 없음)

**Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/order/OrderStatus.java \
        apps/commerce-api/src/main/java/com/loopers/domain/order/OrderModel.java \
        apps/commerce-api/src/test/java/com/loopers/domain/order/OrderModelTest.java
git commit -m "feat: OrderStatus에 결제 흐름 상태 추가 + 상태 전이 메서드 구현"
```

---

## Task 4: PaymentRepository (interface + impl)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentJpaRepository.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentRepositoryImpl.java`

**Step 1: Repository 인터페이스 작성 (domain 레이어)**

```java
package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    List<PaymentModel> findAllByOrderId(Long orderId);
}
```

**Step 2: JPA Repository 작성 (infrastructure 레이어)**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    List<PaymentModel> findAllByOrderId(Long orderId);
}
```

**Step 3: Repository 구현체 작성 (infrastructure 레이어)**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<PaymentModel> findAllByOrderId(Long orderId) {
        return paymentJpaRepository.findAllByOrderId(orderId);
    }
}
```

**Step 4: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentJpaRepository.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentRepositoryImpl.java
git commit -m "feat: PaymentRepository 인터페이스 및 구현체 추가"
```

---

## Task 5: PG 클라이언트 (Config + DTO + Client)

**Files:**
- Modify: `apps/commerce-api/src/main/resources/application.yml`
- Create: `apps/commerce-api/src/main/java/com/loopers/config/PgClientConfig.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/dto/PgPaymentRequest.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/dto/PgPaymentResponse.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgClient.java`

**Step 1: application.yml에 PG 설정 추가**

application.yml의 `springdoc:` 블록 위(spring 블록과 springdoc 사이)에 추가:

```yaml
pg:
  base-url: http://localhost:8082
  callback-url: http://localhost:8080/api/v1/payments/callback
  timeout:
    connect: 1000
    read: 3000
```

**Step 2: PgClientConfig 작성**

```java
package com.loopers.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(PgProperties pgProperties) {
        return new RestTemplateBuilder()
            .rootUri(pgProperties.baseUrl())
            .setConnectTimeout(Duration.ofMillis(pgProperties.timeout().connect()))
            .setReadTimeout(Duration.ofMillis(pgProperties.timeout().read()))
            .build();
    }
}
```

**Step 3: PgProperties 작성 (같은 config 패키지)**

```java
package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg")
public record PgProperties(
    String baseUrl,
    String callbackUrl,
    TimeoutProperties timeout
) {
    public record TimeoutProperties(int connect, int read) {}
}
```

> **주의:** `@EnableConfigurationProperties(PgProperties.class)`를 PgClientConfig에 추가하거나, main Application 클래스에 추가해야 한다. PgClientConfig에 추가하는 것이 응집도가 높다.

PgClientConfig에 어노테이션 추가:
```java
@Configuration
@EnableConfigurationProperties(PgProperties.class)
public class PgClientConfig { ... }
```

**Step 4: PG 요청/응답 DTO 작성**

```java
package com.loopers.infrastructure.payment.dto;

public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    String amount,
    String callbackUrl
) {}
```

```java
package com.loopers.infrastructure.payment.dto;

public record PgPaymentResponse(
    String transactionKey,
    String orderId,
    String status,
    String failureReason
) {}
```

**Step 5: PgClient 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@Component
public class PgClient {

    private final RestTemplate pgRestTemplate;

    public PgPaymentResponse requestPayment(PgPaymentRequest request, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);

        HttpEntity<PgPaymentRequest> httpEntity = new HttpEntity<>(request, headers);
        return pgRestTemplate.postForObject("/api/v1/payments", httpEntity, PgPaymentResponse.class);
    }

    public PgPaymentResponse getPaymentStatus(String transactionKey, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);
        return pgRestTemplate.exchange(
            "/api/v1/payments/{transactionKey}",
            org.springframework.http.HttpMethod.GET,
            httpEntity,
            PgPaymentResponse.class,
            transactionKey
        ).getBody();
    }
}
```

**Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/resources/application.yml \
        apps/commerce-api/src/main/java/com/loopers/config/PgClientConfig.java \
        apps/commerce-api/src/main/java/com/loopers/config/PgProperties.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/dto/PgPaymentRequest.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/dto/PgPaymentResponse.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgClient.java
git commit -m "feat: PG 클라이언트 + RestTemplate 설정 추가"
```

---

## Task 6: PaymentService (도메인 서비스)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentService.java`

**Step 1: PaymentService 작성**

```java
package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel save(PaymentModel payment) {
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 거래를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> getByOrderId(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId);
    }
}
```

**Step 2: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentService.java
git commit -m "feat: PaymentService 도메인 서비스 추가"
```

---

## Task 7: PaymentCommand + PaymentInfo (Application DTO)

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentCommand.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentInfo.java`

**Step 1: PaymentCommand 작성**

```java
package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand(
    Long orderId,
    CardType cardType,
    String cardNo  // 원본 카드번호 (마스킹 전)
) {
    /**
     * 카드번호 마스킹: "1234-5678-9814-1451" → "1234-****-****-1451"
     */
    public String maskedCardNo() {
        if (cardNo == null || cardNo.length() < 4) return cardNo;
        String digitsOnly = cardNo.replaceAll("-", "");
        if (digitsOnly.length() < 8) return cardNo;
        String first4 = digitsOnly.substring(0, 4);
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        return first4 + "-****-****-" + last4;
    }
}
```

**Step 2: PaymentInfo 작성**

```java
package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;

import java.time.ZonedDateTime;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    Long userId,
    String cardType,
    String maskedCardNo,
    int amount,
    String status,
    String transactionKey,
    String failureReason,
    ZonedDateTime createdAt
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.orderId(),
            payment.userId(),
            payment.cardType().name(),
            payment.maskedCardNo(),
            payment.amount().value(),
            payment.status().name(),
            payment.transactionKey(),
            payment.failureReason(),
            payment.getCreatedAt()
        );
    }
}
```

**Step 3: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentCommand.java \
        apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentInfo.java
git commit -m "feat: PaymentCommand, PaymentInfo DTO 추가"
```

---

## Task 8: ErrorType 확장

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java`

**Step 1: 결제 관련 ErrorType 추가**

ErrorType.java의 마지막 값(`AUTHENTICATION_FAILED`) 뒤에 추가:

```java
PG_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PG Request Failed", "결제 시스템 요청에 실패했습니다."),
PG_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PG Timeout", "결제 시스템 응답 시간이 초과되었습니다.");
```

**Step 2: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java
git commit -m "feat: 결제 관련 ErrorType 추가 (PG_REQUEST_FAILED, PG_TIMEOUT)"
```

---

## Task 9: PaymentFacade (핵심 유스케이스) (Red → Green)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeTest.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java`

**Step 1: 실패하는 테스트 작성 (Unit Test — Mock 기반)**

```java
package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.config.PgProperties;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.infrastructure.payment.PgClient;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @InjectMocks private PaymentFacade paymentFacade;
    @Mock private PaymentService paymentService;
    @Mock private OrderService orderService;
    @Mock private PgClient pgClient;
    @Mock private PgProperties pgProperties;

    @DisplayName("결제 요청")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청 성공 시 PENDING 상태의 PaymentInfo를 반환한다")
        @Test
        void returnsPendingPaymentOnSuccess() {
            // given
            Long userId = 1L;
            PaymentCommand command = new PaymentCommand(1L, CardType.SAMSUNG, "1234-5678-9814-1451");

            OrderModel mockOrder = mock(OrderModel.class);
            given(mockOrder.getId()).willReturn(1L);
            given(mockOrder.finalAmount()).willReturn(new Money(50000));
            given(mockOrder.status()).willReturn(OrderStatus.CREATED);
            given(orderService.getOrder(1L, userId)).willReturn(mockOrder);

            given(pgProperties.callbackUrl()).willReturn("http://localhost:8080/api/v1/payments/callback");
            given(pgClient.requestPayment(any(), eq(String.valueOf(userId))))
                .willReturn(new PgPaymentResponse("20250816:TR:abc123", "1", "PENDING", null));

            given(paymentService.save(any(PaymentModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentInfo result = paymentFacade.requestPayment(userId, command);

            // then
            assertAll(
                () -> assertThat(result.status()).isEqualTo("PENDING"),
                () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:abc123"),
                () -> assertThat(result.maskedCardNo()).isEqualTo("1234-****-****-1451")
            );
            verify(mockOrder).startPayment();
        }

        @DisplayName("본인 주문이 아니면 예외가 발생한다")
        @Test
        void throwsWhenNotOrderOwner() {
            // given
            Long userId = 999L;
            PaymentCommand command = new PaymentCommand(1L, CardType.SAMSUNG, "1234-5678-9814-1451");
            given(orderService.getOrder(1L, userId)).willThrow(new CoreException(
                com.loopers.support.error.ErrorType.BAD_REQUEST, "본인의 주문만 조회할 수 있습니다."
            ));

            // when & then
            assertThrows(CoreException.class, () -> paymentFacade.requestPayment(userId, command));
        }
    }

    @DisplayName("콜백 처리")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신 시 결제 성공 + 주문 확인 처리된다")
        @Test
        void handlesSuccessCallback() {
            // given
            String transactionKey = "20250816:TR:abc123";
            PaymentModel mockPayment = mock(PaymentModel.class);
            given(mockPayment.orderId()).willReturn(1L);
            given(paymentService.getByTransactionKey(transactionKey)).willReturn(mockPayment);

            OrderModel mockOrder = mock(OrderModel.class);
            given(orderService.getOrderForAdmin(1L)).willReturn(mockOrder);

            // when
            paymentFacade.handleCallback(transactionKey, "SUCCESS", null);

            // then
            verify(mockPayment).markSuccess();
            verify(mockOrder).confirmPayment();
        }

        @DisplayName("FAILED 콜백 수신 시 결제 실패 + 주문 실패 처리된다")
        @Test
        void handlesFailedCallback() {
            // given
            String transactionKey = "20250816:TR:abc123";
            PaymentModel mockPayment = mock(PaymentModel.class);
            given(mockPayment.orderId()).willReturn(1L);
            given(paymentService.getByTransactionKey(transactionKey)).willReturn(mockPayment);

            OrderModel mockOrder = mock(OrderModel.class);
            given(orderService.getOrderForAdmin(1L)).willReturn(mockOrder);

            // when
            paymentFacade.handleCallback(transactionKey, "FAILED", "한도 초과");

            // then
            verify(mockPayment).markFailed("한도 초과");
            verify(mockOrder).failPayment();
        }
    }
}
```

**Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "PaymentFacadeTest"
```
Expected: FAIL (PaymentFacade 클래스 없음)

**Step 3: PaymentFacade 구현**

```java
package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.config.PgProperties;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.infrastructure.payment.PgClient;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClient pgClient;
    private final PgProperties pgProperties;

    /**
     * 결제 요청 흐름:
     * 1. [TX-1] 주문 조회 + 주문 상태를 PAYMENT_PENDING으로 변경 + 결제 레코드 생성 (PENDING)
     * 2. [TX 없음] PG 시스템 호출 → transactionKey 수신
     * 3. [TX-2] transactionKey 업데이트
     */
    public PaymentInfo requestPayment(Long userId, PaymentCommand command) {
        // TX-1: 주문 검증 + 결제 레코드 생성
        PaymentModel payment = createPaymentRecord(userId, command);

        // TX 없음: PG 호출 (트랜잭션 밖)
        PgPaymentResponse pgResponse = callPg(userId, command, payment);

        // TX-2: transactionKey 업데이트
        updateTransactionKey(payment.getId(), pgResponse.transactionKey());

        return PaymentInfo.from(payment);
    }

    @Transactional
    protected PaymentModel createPaymentRecord(Long userId, PaymentCommand command) {
        OrderModel order = orderService.getOrder(command.orderId(), userId);
        order.startPayment();

        PaymentModel payment = new PaymentModel(
            order.getId(),
            userId,
            command.cardType(),
            command.maskedCardNo(),
            order.finalAmount()
        );
        return paymentService.save(payment);
    }

    private PgPaymentResponse callPg(Long userId, PaymentCommand command, PaymentModel payment) {
        PgPaymentRequest pgRequest = new PgPaymentRequest(
            String.valueOf(command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            String.valueOf(payment.amount().value()),
            pgProperties.callbackUrl()
        );

        try {
            PgPaymentResponse pgResponse = pgClient.requestPayment(pgRequest, String.valueOf(userId));
            if (pgResponse == null || pgResponse.transactionKey() == null) {
                throw new CoreException(ErrorType.PG_REQUEST_FAILED, "PG 응답이 유효하지 않습니다.");
            }
            return pgResponse;
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            throw new CoreException(ErrorType.PG_REQUEST_FAILED, "PG 결제 요청에 실패했습니다: " + e.getMessage());
        }
    }

    @Transactional
    protected void updateTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = paymentService.getById(paymentId);
        payment.assignTransactionKey(transactionKey);
    }

    /**
     * PG 콜백 수신 처리
     */
    @Transactional
    public void handleCallback(String transactionKey, String status, String failureReason) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        OrderModel order = orderService.getOrderForAdmin(payment.orderId());

        if ("SUCCESS".equals(status)) {
            payment.markSuccess();
            order.confirmPayment();
        } else if ("FAILED".equals(status)) {
            payment.markFailed(failureReason);
            order.failPayment();
        }
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        return PaymentInfo.from(paymentService.getById(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsByOrderId(Long orderId) {
        return paymentService.getByOrderId(orderId).stream()
            .map(PaymentInfo::from)
            .toList();
    }
}
```

**Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "PaymentFacadeTest"
```
Expected: ALL PASS

**Step 5: 커밋**

```bash
git add apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeTest.java \
        apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java
git commit -m "feat: PaymentFacade 결제 요청/콜백 유스케이스 구현 (TDD)"
```

---

## Task 10: PaymentV1Dto + PaymentV1ApiSpec + PaymentV1Controller

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Dto.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1ApiSpec.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Controller.java`

**Step 1: PaymentV1Dto 작성**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        String cardType,
        String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, CardType.valueOf(cardType), cardNo);
        }
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String cardType,
        String maskedCardNo,
        int amount,
        String status,
        String failureReason,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.transactionKey(),
                info.cardType(),
                info.maskedCardNo(),
                info.amount(),
                info.status(),
                info.failureReason(),
                info.createdAt()
            );
        }
    }

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String status,
        String failureReason
    ) {}
}
```

**Step 2: PaymentV1ApiSpec 작성**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        MemberModel member, PaymentV1Dto.PaymentRequest request
    );

    @Operation(summary = "결제 상태 조회", description = "결제 상세 정보를 조회합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
        MemberModel member, Long paymentId
    );

    @Operation(summary = "주문별 결제 내역 조회", description = "주문에 대한 모든 결제 시도를 조회합니다.")
    ApiResponse<List<PaymentV1Dto.PaymentResponse>> getPaymentsByOrderId(
        MemberModel member, Long orderId
    );

    @Operation(summary = "PG 콜백 수신", description = "PG 시스템으로부터 결제 결과를 수신합니다.")
    ApiResponse<Object> handleCallback(PaymentV1Dto.CallbackRequest request);
}
```

**Step 3: PaymentV1Controller 작성**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping("/api/v1/payments")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @LoginMember MemberModel member,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(member.getId(), request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
        @LoginMember MemberModel member,
        @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.getPayment(paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments")
    @Override
    public ApiResponse<List<PaymentV1Dto.PaymentResponse>> getPaymentsByOrderId(
        @LoginMember MemberModel member,
        @RequestParam Long orderId
    ) {
        List<PaymentInfo> infos = paymentFacade.getPaymentsByOrderId(orderId);
        List<PaymentV1Dto.PaymentResponse> responses = infos.stream()
            .map(PaymentV1Dto.PaymentResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PostMapping("/api/v1/payments/callback")
    @Override
    public ApiResponse<Object> handleCallback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.failureReason());
        return ApiResponse.success();
    }
}
```

**Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Dto.java \
        apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1ApiSpec.java \
        apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Controller.java
git commit -m "feat: Payment API 컨트롤러 + DTO + Swagger Spec 추가"
```

---

## Task 11: 통합 테스트 (PaymentFacade Integration)

**Files:**
- Create: `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeIntegrationTest.java`

**Step 1: 통합 테스트 작성**

> PG 클라이언트는 MockRestServiceServer로 모킹한다. 나머지는 실제 Spring Context + DB를 사용한다.

```java
package com.loopers.application.payment;

import com.loopers.application.brand.BrandService;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderResult;
import com.loopers.application.order.OrderService;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private ProductFacade productFacade;
    @Autowired private BrandService brandService;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired @Qualifier("pgRestTemplate") private RestTemplate pgRestTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(pgRestTemplate);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderResult createOrder() {
        Long brandId = brandService.register("나이키", "스포츠").getId();
        Long productId = productFacade.register("에어맥스", "러닝화", new Money(50000), brandId, 10).id();
        return orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)), null);
    }

    @DisplayName("결제 요청")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청 성공 시 PENDING 결제가 생성되고 주문 상태가 PAYMENT_PENDING으로 변경된다")
        @Test
        void createsPendingPayment() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                    """
                    {"transactionKey":"20250816:TR:test123","orderId":"%d","status":"PENDING","failureReason":null}
                    """.formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment(1L, command);

            // then
            assertAll(
                () -> assertThat(result.status()).isEqualTo("PENDING"),
                () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:test123"),
                () -> assertThat(result.maskedCardNo()).isEqualTo("1234-****-****-1451"),
                () -> assertThat(result.amount()).isEqualTo(50000)
            );

            // 주문 상태 확인
            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);

            mockServer.verify();
        }
    }

    @DisplayName("콜백 처리")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 시 결제 성공 + 주문 CONFIRMED 처리")
        @Test
        void handlesSuccessCallback() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("/api/v1/payments"))
                .andRespond(withSuccess(
                    """
                    {"transactionKey":"20250816:TR:cb001","orderId":"%d","status":"PENDING","failureReason":null}
                    """.formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentFacade.requestPayment(1L, command);

            // when
            paymentFacade.handleCallback("20250816:TR:cb001", "SUCCESS", null);

            // then
            PaymentInfo payment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            assertThat(payment.status()).isEqualTo("SUCCESS");

            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("FAILED 콜백 시 결제 실패 + 주문 PAYMENT_FAILED 처리")
        @Test
        void handlesFailedCallback() {
            // given
            OrderResult orderResult = createOrder();
            Long orderId = orderResult.order().getId();

            mockServer.expect(requestTo("/api/v1/payments"))
                .andRespond(withSuccess(
                    """
                    {"transactionKey":"20250816:TR:cb002","orderId":"%d","status":"PENDING","failureReason":null}
                    """.formatted(orderId),
                    MediaType.APPLICATION_JSON
                ));

            PaymentCommand command = new PaymentCommand(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentFacade.requestPayment(1L, command);

            // when
            paymentFacade.handleCallback("20250816:TR:cb002", "FAILED", "한도 초과");

            // then
            PaymentInfo payment = paymentFacade.getPaymentsByOrderId(orderId).get(0);
            assertAll(
                () -> assertThat(payment.status()).isEqualTo("FAILED"),
                () -> assertThat(payment.failureReason()).isEqualTo("한도 초과")
            );

            OrderModel order = orderService.getOrderForAdmin(orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }
    }
}
```

**Step 2: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "PaymentFacadeIntegrationTest"
```
Expected: ALL PASS

**Step 3: 커밋**

```bash
git add apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeIntegrationTest.java
git commit -m "test: PaymentFacade 통합 테스트 추가 (MockRestServiceServer)"
```

---

## Task 12: HTTP 파일 작성 + 전체 테스트 실행

**Files:**
- Create: `.http/payment.http`

**Step 1: HTTP 파일 작성**

```http
### 결제 요청
POST http://localhost:8080/api/v1/payments
X-Loopers-LoginId: user1
X-Loopers-LoginPw: password1
Content-Type: application/json

{
  "orderId": 1,
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}

### 결제 상태 조회
GET http://localhost:8080/api/v1/payments/1
X-Loopers-LoginId: user1
X-Loopers-LoginPw: password1

### 주문별 결제 내역 조회
GET http://localhost:8080/api/v1/payments?orderId=1
X-Loopers-LoginId: user1
X-Loopers-LoginPw: password1

### PG 콜백 (수동 테스트용)
POST http://localhost:8080/api/v1/payments/callback
Content-Type: application/json

{
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1",
  "status": "SUCCESS",
  "failureReason": null
}
```

**Step 2: 전체 테스트 실행**

```bash
./gradlew test
```
Expected: ALL PASS

**Step 3: 커밋**

```bash
git add .http/payment.http
git commit -m "docs: 결제 API HTTP 테스트 파일 추가"
```

---

## 구현 순서 요약

| Task | 내용 | 유형 | 검증 |
|------|------|------|------|
| 1 | PaymentStatus, CardType Enum | 생성 | 컴파일 |
| 2 | PaymentModel 엔티티 | Red→Green | PaymentModelTest |
| 3 | OrderStatus 확장 + 상태 전이 | Red→Green | OrderModelTest + 기존 Order 테스트 |
| 4 | PaymentRepository (interface + impl) | 생성 | 컴파일 |
| 5 | PG 클라이언트 (Config + DTO + Client) | 생성 | 컴파일 |
| 6 | PaymentService | 생성 | 컴파일 |
| 7 | PaymentCommand, PaymentInfo DTO | 생성 | 컴파일 |
| 8 | ErrorType 확장 | 수정 | 컴파일 |
| 9 | PaymentFacade | Red→Green | PaymentFacadeTest (Unit) |
| 10 | Controller + API DTO + Spec | 생성 | 컴파일 |
| 11 | 통합 테스트 | 테스트 | PaymentFacadeIntegrationTest |
| 12 | HTTP 파일 + 전체 검증 | 문서 | 전체 테스트 |

---

## 이 Plan에서 제외한 것 (후속 브랜치)

| 항목 | 브랜치 | 이유 |
|------|--------|------|
| CircuitBreaker / Timeout / Fallback | `week6-feature/resilience` | 장애 대응은 순수 도메인 이후 |
| Retry 정책 | `week6-feature/resilience` | Nice-to-have |
| 콜백 미수신 시 폴링/복구 | `week6-feature/payment-callback` | PENDING 상태 복구 로직 |
| 스케줄러 기반 자동 확인 | `week6-feature/payment-callback` | 운영 안정성 |
| E2E 테스트 | Task 11 이후 추가 가능 | 통합 테스트로 충분히 검증 후 판단 |

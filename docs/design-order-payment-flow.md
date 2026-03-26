# 주문-결제 자동화 플로우 설계

## 요구사항

1. **주문하기** 버튼 → 주문 생성 + 결제 자동 실행 (한 번에)
2. 결제 실패 시 → 사용자가 직접 결제 재시도 가능
3. 첫 결제 시 카드 정보 저장, 이후 주문 시 저장된 카드 자동 사용
4. 카드는 여러 개 등록 가능, 기본 카드(is_default) 존재

---

## 설계 결정과 이유

### 1. Order와 Payment는 왜 같은 트랜잭션이 아닌가

Payment 생성이 실패해도 주문은 성공해야 한다.
Payment는 Order와 별도의 관심사이기 때문에 TX를 분리하고 이벤트로 연결한다.

```
Order 관심사: 무엇을, 얼마에 샀는가
Payment 관심사: 어떤 카드로, 어떻게 결제했는가
```

### 2. 카드 정보는 왜 OrderCreatedEvent에 담는가

| 후보 | 이유 |
|---|---|
| Order 테이블에 저장 | ❌ Payment의 관심사, Order가 알 필요 없음 |
| user_card에서 조회 | ❌ 리스너 B(카드 저장)가 실패했을 수 있음 |
| OrderCreatedEvent에 포함 | ✅ 이벤트 발행 시점에 카드 정보가 확실히 존재 |

### 3. user_card는 왜 별도 테이블인가

- 카드가 여러 개 등록 가능 → 1:N 관계 → 별도 테이블
- Users 테이블(회원 정보)과 카드 정보는 변경 빈도와 성격이 다름

### 4. 각 로직의 실패 허용 범위

| 로직 | 실패 시 주문 영향 | 처리 방식 |
|---|---|---|
| Order 생성 | 주문 실패 | 메인 TX |
| Payment(PENDING) 생성 | 주문 성공 유지 | 이벤트 리스너 A |
| PG 결제 실행 | 주문 성공 유지 | 이벤트 리스너 A |
| user_card 저장 | 주문 성공 유지 | 이벤트 리스너 B |
| user_card 저장 실패 | 고객 알림 발송 | 리스너 B 실패 처리 |

---

## 플로우

### Flow 1: 주문 + 자동 결제

```
POST /api/v1/orders
  body: { items, cardType, cardNo }

OrderFacade.createOrder()
  └─ Order 생성 TX → 커밋
       └─ OrderCreatedEvent(orderId, memberId, amount, cardType, cardNo) 발행
            │
            ├─ 리스너 A (AFTER_COMMIT, Async, REQUIRES_NEW)
            │    └─ Payment(PENDING) 생성
            │    └─ PG 결제 요청
            │
            └─ 리스너 B (AFTER_COMMIT, Async, REQUIRES_NEW)
                 └─ user_card 저장 (is_default = true)
                 └─ 실패 시 → 고객 알림 로그
```

### Flow 2: 결제 재시도 (기존 API 유지)

```
POST /api/v1/payments
  body: { orderId, cardType, cardNo }

PaymentFacade.pay()
  └─ Payment(PENDING) 생성 TX → 커밋
  └─ PG 결제 요청
```

---

## ERD 변경

### 신규: user_card 테이블

```
user_card
  - id           BIGINT PK
  - user_id      BIGINT FK → users.id
  - card_type    VARCHAR(20)
  - card_no      VARCHAR(25)
  - is_default   BOOLEAN
  - created_at   DATETIME
  - updated_at   DATETIME
  - deleted_at   DATETIME
```

---

## 카드 정보 필수 검증

카드 정보 없이는 주문 불가. `OrderFacade.createOrder()`에서 null 체크 후 `400 BAD_REQUEST` 반환.
테스트에도 예외를 두지 않는다. 실제 동작과 테스트가 일치해야 한다.

---

## 구현 목록

| 항목 | 파일 |
|---|---|
| UserCard 엔티티 | `domain/usercard/UserCard.java` |
| UserCardRepository | `domain/usercard/UserCardRepository.java` |
| UserCardService | `domain/usercard/UserCardService.java` |
| UserCardJpaRepository | `infrastructure/usercard/UserCardJpaRepository.java` |
| UserCardRepositoryImpl | `infrastructure/usercard/UserCardRepositoryImpl.java` |
| OrderCreatedEvent | `domain/order/OrderCreatedEvent.java` |
| OrderEventListener | `domain/order/OrderEventListener.java` |
| OrderFacade 수정 | 카드 정보 파라미터 추가 + 이벤트 발행 |
| OrderV1Dto 수정 | CreateOrderRequest에 cardType, cardNo 추가 |
| OrderPaymentEventListener | `domain/payment/` — OrderCreatedEvent → Payment 생성 + PG 호출 |
| OrderUserCardEventListener | `domain/usercard/` — OrderCreatedEvent → 카드 저장 |
| OrderCreatedEventTest | OrderCreatedEvent 발행 검증 |
| UserCardServiceIntegrationTest | 카드 저장 로직 검증 |
| OrderV1ApiE2ETest 수정 | 모든 주문 요청에 카드 정보 추가 |
| PaymentV1ApiE2ETest 수정 | createOrder 헬퍼에 카드 정보 추가 |

---

## 예외 처리 전략

| 리스너 | 예외 처리 | 이유 |
|---|---|---|
| `OrderPaymentEventListener` | try/catch 없음 (예외 전파) | 결제 생성 실패는 추적 필요 |
| `OrderUserCardEventListener` | try/catch로 감쌈 | 카드 저장 실패는 주문에 영향 없어야 함 |

두 리스너 모두 `@Async`라서 메인 스레드(주문 응답)에는 영향 없다.

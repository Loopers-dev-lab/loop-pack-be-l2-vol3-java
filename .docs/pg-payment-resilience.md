# PG 결제 연동 + Resilience 설계

## 1. 배경

기존 요구사항(01-requirements.md)에서 "결제"와 "주문 상태 관리"는 명시적으로 제외 범위였다.
이번 단계에서 외부 PG 시스템과의 비동기 결제 연동을 추가하고, resilience 패턴을 적용한다.

### 1.1 PG 비동기 결제 방식

```
클라이언트 → 우리 서버 → PG 서버
                         ↓ (1~5초 후)
클라이언트 ← 우리 서버 ← PG 서버 (콜백)
```

- PG는 결제 요청 시 즉시 "접수됨(ACCEPTED)"을 반환
- 1~5초 후 콜백(webhook)으로 결제 결과를 전달
- 콜백 미수신 시 폴링으로 복구

### 1.2 설계 결정

| 결정 | 선택 | 근거 |
|------|------|------|
| API 분리 | 주문 생성과 결제 요청을 별도 API로 분리 | 주문 = 비즈니스 로직, 결제 = 외부 연동. 관심사 분리 |
| HTTP Client | RestClient (Spring 6.1+) | Spring Boot 3.4에 내장, WebClient보다 동기 호출에 적합 |
| Resilience | Resilience4j | Spring Boot 3 공식 지원, 어노테이션 기반 |
| 재고 복구 | 결제 실패 시 즉시 복구 | 보상 트랜잭션 패턴 (Saga) |

---

## 2. 도메인 모델 변경

### 2.1 Order 상태 머신

```
PENDING_PAYMENT (주문 생성 직후)
    ├── → PAID (결제 성공)
    ├── → PAYMENT_FAILED (결제 실패)
    └── → PAYMENT_TIMEOUT (결제 타임아웃)
```

- 상태 전이는 `PENDING_PAYMENT`에서만 가능 (단방향)
- `Order` 엔티티에 `status` 필드 추가 (`failureReason`은 Payment 엔티티에서만 관리)

### 2.2 Payment 엔티티 (신규 도메인)

```
REQUESTED (PG에 결제 요청)
    ├── → SUCCESS (PG 결제 성공)
    ├── → FAILED (PG 결제 실패)
    └── → TIMEOUT (PG 응답 없음)
```

**필드**: `orderId`(UNIQUE), `userId`, `transactionId`(PG 발급, nullable), `status`, `amount`, `cardType`, `cardNo`, `failureReason`, `pgRespondedAt`

**`@Version` 낙관적 락**: 콜백 + 폴링이 동시에 같은 Payment를 처리하는 경우 방어

### 2.3 유비쿼터스 언어 추가

| 한국어 | 영문 | 정의 |
|--------|------|------|
| 결제 | Payment | PG를 통해 주문 금액을 결제하는 행위 |
| PG | Payment Gateway | 외부 결제 대행사 시스템 |
| 콜백 | Callback (Webhook) | PG가 결제 결과를 우리 서버에 전달하는 비동기 알림 |
| 서킷 브레이커 | Circuit Breaker | 외부 시스템 장애 시 빠르게 실패하여 장애 전파를 차단하는 패턴 |
| 재시도 | Retry | 일시적 네트워크 오류 시 자동으로 요청을 재시도하는 패턴 |
| 폴백 | Fallback | 외부 시스템 호출 실패 시 대체 처리하는 로직 |
| 보상 트랜잭션 | Compensating Transaction | 실패 시 이전 상태로 되돌리는 역방향 트랜잭션 (재고 복구 등) |

---

## 3. 아키텍처 설계

### 3.1 레이어별 파일 구성

```
domain/payment/
    Payment.java            # 결제 엔티티
    PaymentStatus.java      # 결제 상태 enum
    PaymentService.java     # 결제 도메인 서비스
    PaymentRepository.java  # 결제 레포지토리 인터페이스
    PgClient.java           # PG 클라이언트 인터페이스 (Port)

infrastructure/payment/
    PaymentRepositoryImpl.java  # 결제 레포지토리 구현체
    PaymentJpaRepository.java   # Spring Data JPA
    PgClientImpl.java           # PG 클라이언트 구현체 (Adapter, RestClient + Resilience4j)

application/payment/
    PaymentFacade.java          # 결제 유스케이스 조율
    PaymentInfo.java            # 결제 응용 DTO
    PaymentRequestEvent.java    # 결제 요청 이벤트
    PaymentEventListener.java   # AFTER_COMMIT 이벤트 리스너
    PaymentRecoveryScheduler.java  # 미수신 콜백 복구 스케줄러

interfaces/api/payment/
    PaymentV1Controller.java    # 결제 API 컨트롤러
    PaymentV1Dto.java           # API 요청/응답 DTO
    PaymentV1ApiSpec.java       # Swagger 스펙
```

### 3.2 의존성 방향

```
PaymentV1Controller → PaymentFacade → PaymentService (도메인)
                                    → PgClient (인터페이스)
                                                ↑
                                          PgClientImpl (인프라, Resilience4j 적용)
```

### 3.3 트랜잭션 경계 설계

```
[클라이언트] POST /api/v1/payments { orderId, cardType, cardNo }

[TRANSACTION 1 — PaymentFacade.requestPayment()]
  ① Order 조회 + 소유권 확인 + 상태 확인 (PENDING_PAYMENT)
  ② Payment 엔티티 생성 (status=REQUESTED)
  ③ PaymentRequestEvent 발행
  → COMMIT

[AFTER_COMMIT — PaymentEventListener]
  ④ PG API 호출 (RestClient + Resilience4j 보호)
  ⑤-a. PG 접수 성공 → Payment에 transactionId 저장 (새 TX)
  ⑤-b. PG 호출 실패 (fallback) → Payment TIMEOUT + Order PAYMENT_TIMEOUT (새 TX)
```

**핵심 설계 이유**: PG 호출을 `AFTER_COMMIT`에서 실행하면 DB 커넥션을 점유하지 않음.
외부 API 호출 중 DB 커넥션 풀이 고갈되는 것을 방지한다.

---

## 4. Resilience 패턴 설계

### 4.1 적용 패턴

| 패턴 | 목적 | 적용 위치 |
|------|------|-----------|
| Circuit Breaker | PG 장애 시 빠른 실패 → 내부 시스템 보호 | `PgClientImpl` |
| Retry | 일시적 네트워크 오류 자동 복구 | `PgClientImpl` |
| Timeout | 느린 응답 차단 | RestClient 설정 |
| Fallback | 장애 시 대체 처리 (TIMEOUT 마킹) | `PgClientImpl` |

### 4.2 설정값 근거

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgPayment:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10         # 최근 10건 기준
        minimum-number-of-calls: 5      # 최소 5건 이후 평가 시작
        failure-rate-threshold: 50      # 실패율 50% 이상이면 OPEN
        wait-duration-in-open-state: 30s  # OPEN 후 30초 대기
        permitted-number-of-calls-in-half-open-state: 3  # HALF_OPEN에서 3건 시도
        slow-call-duration-threshold: 2s  # 2초 초과 = slow call
        slow-call-rate-threshold: 80     # slow call 80% 이상이면 OPEN
  retry:
    instances:
      pgPayment:
        max-attempts: 2                  # 최대 2회 시도 (원본 + 1회 재시도)
        wait-duration: 500ms             # 재시도 간격 500ms
        retry-exceptions:
          - java.net.ConnectException      # 연결 실패만 재시도
          - java.net.SocketTimeoutException # 타임아웃만 재시도
```

**설정값 근거**:
- **Timeout 3s**: PG 정상 응답 100~500ms 대비 6배 여유
- **CircuitBreaker 50%/10건**: transport 실패만 카운트. 비즈니스 실패(잔액 부족 등)는 제외
- **Retry 2회**: ConnectException/SocketTimeout만 재시도. 결제는 멱등성 불확실하므로 신중하게
- **Open 30s**: PG 복구 시간 확보. 너무 짧으면 회복 전에 다시 요청해서 장애 악화

---

## 5. 콜백 + 폴링 복구

### 5.1 콜백 엔드포인트

```
POST /api/v1/payments/callback  (인증 제외)
```

PG가 결제 결과를 전달하는 webhook. `transactionId` 기준으로 Payment 조회 후 상태 갱신.

### 5.2 멱등성 보장

- Payment가 terminal 상태(SUCCESS, FAILED, TIMEOUT)이면 콜백 무시
- `@Version` 낙관적 락으로 콜백 + 폴링 동시 처리 방어

### 5.3 미수신 콜백 복구 스케줄러

```
@Scheduled(fixedDelay = 30000)  // 30초 간격
```

- `REQUESTED` 상태 + 30초 이상 경과한 Payment 조회
- PG 결제 상태 확인 API 호출 → 콜백 처리 로직 재사용
- `REQUESTED` 상태 + 5분 이상 경과 → TIMEOUT 처리 + 재고 복구

### 5.4 보상 트랜잭션 (재고 복구)

결제 실패/타임아웃 시:
1. `Order.orderItems` 기반으로 각 상품 재고 복구 (`Product.increaseStock()`)
2. 쿠폰 사용 취소 (선택적, `UserCoupon.cancelUse()`)

---

## 6. 기존 파일 수정 사항

| 파일 | 변경 내용 |
|------|-----------|
| `apps/commerce-api/build.gradle.kts` | resilience4j + aop 의존성 추가 |
| `apps/commerce-api/src/main/resources/application.yml` | PG URL, resilience4j 설정 추가 |
| `domain/order/Order.java` | `status`, `failureReason` 필드 + 상태 전이 메서드 |
| `domain/order/OrderStatus.java` | 신규 enum |
| `application/order/OrderInfo.java` | `status`, `failureReason` 필드 추가 |
| `interfaces/api/order/OrderV1Dto.java` | 응답에 `status` 포함 |
| `interfaces/api/order/OrderAdminV1Dto.java` | 응답에 `status` 포함 |
| `config/WebMvcConfig.java` | 콜백 경로 인증 제외 |

---

## 7. 에지 케이스

| 케이스 | 처리 방식 |
|--------|-----------|
| 중복 결제 요청 | `payments.order_id` UNIQUE 제약 → CONFLICT(409) |
| 콜백 + 폴링 동시 도착 | `@Version` 낙관적 락 → 먼저 도착한 쪽이 처리, 늦은 쪽은 OptimisticLockException |
| PG 접수 후 콜백 미수신 | 폴링 스케줄러가 30초 후 PG 상태 확인 API로 복구 |
| PG 완전 장애 | CircuitBreaker OPEN → fallback으로 즉시 TIMEOUT 마킹 + 재고 복구 |
| 5분 초과 미응답 | 최종 타임아웃 → TIMEOUT 처리 + 재고 복구 |

---

## 8. 구현 순서 (TDD: Red → Green → Refactor)

| 순서 | 작업 | 테스트 타입 |
|------|------|------------|
| 1 | OrderStatus enum + Order 상태 전이 | 단위 테스트 |
| 2 | Payment 엔티티 + PaymentStatus + 상태 전이 | 단위 테스트 |
| 3 | PaymentRepository + PaymentService | 통합 테스트 |
| 4 | build.gradle.kts 의존성 + Resilience4j 설정 | - |
| 5 | PgClient 인터페이스 + PgClientImpl (RestClient) | WireMock 테스트 |
| 6 | PaymentFacade.requestPayment() + 이벤트 리스너 | Facade 통합 테스트 |
| 7 | PaymentV1Controller (결제 요청 API) | E2E 테스트 |
| 8 | 콜백 엔드포인트 + 콜백 처리 로직 | E2E 테스트 |
| 9 | 재고 복구 로직 | 통합 테스트 |
| 10 | PaymentRecoveryScheduler (폴링 복구) | 통합 테스트 |
| 11 | 결제 상태 조회 API | E2E 테스트 |

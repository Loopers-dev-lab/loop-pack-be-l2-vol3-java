# 결제 기능 구현 계획

> 본 문서는 PG-Simulator 비동기 결제 연동 및 Resilience 설계를 위한 구현 계획이다.
> 참고: `resilience-learning-roadmap.md`, `skills/analize_external_integration/SKILL.md`

---

## 1. 현재 프로젝트 주문/결제 구조

### 1.1 기존 구성


| 계층              | 클래스                 | 역할                                                                            |
| --------------- | ------------------- | ----------------------------------------------------------------------------- |
| **interfaces**  | `OrderV1Controller` | `POST /orders`, `GET /orders/{id}`, `GET /orders`, `POST /orders/{id}/cancel` |
| **application** | `OrderFacade`       | `create`, `findById`, `findOrders`, `cancel` — 트랜잭션 경계                        |
| **domain**      | `OrderService`      | `create`(재고 검증만), `cancel`(PAID 시 재고 복구)                                      |
| **domain**      | `OrderModel`        | `ORDERED` 생성, `cancel()` — `**markPaid()` 없음**                                |
| **domain**      | `OrderStatus`       | ORDERED, PAID, SHIPPING, DELIVERED, CANCELLED                                 |
| **domain**      | `ProductService`    | `validateAndGetSnapshots`, `restoreStock` — `**decreaseStock` 없음**            |


### 1.2 결제 관련 설계 포인트

- 주문 생성 시: 재고 **검증만**, 차감 없음
- 재고 차감: **결제 완료 시점**에 수행 (01-requirements §3.1)
- `OrderModel`에 `markPaid()` 없음 → 추가 필요
- `ProductService`에 `decreaseStock` 없음 → 추가 필요
- PG 연동, Resilience4j 미도입 → `build.gradle.kts`에 추가 필요

---

## 2. 요구사항 요약

### 2.1 API 스펙


| 구분               | 엔드포인트                               | 설명                                             |
| ---------------- | ----------------------------------- | ---------------------------------------------- |
| **commerce-api** | `POST /api/v1/payments`             | 주문 ID, 카드 타입, 카드 번호 입력 → PG 요청                 |
| **PG-Simulator** | `POST /api/v1/payments`             | orderId, cardType, cardNo, amount, callbackUrl |
| **PG-Simulator** | `GET /api/v1/payments/{paymentId}`  | 결제 정보 확인                                       |
| **PG-Simulator** | `GET /api/v1/payments?orderId={id}` | 주문별 결제 정보 조회                                   |


### 2.2 비동기 결제 특성

- **요청 성공 확률**: 60%
- **요청 지연**: 100ms ~ 500ms
- **처리 지연**: 1s ~ 5s
- **처리 결과**: 성공 70% / 한도 초과 20% / 잘못된 카드 10%
- **POST 응답**: "접수됨"이지 "결제 완료"가 아님

### 2.3 과제 체크리스트

- PG 연동: RestTemplate 또는 FeignClient
- 타임아웃 설정, 실패 시 예외 처리
- 결제 요청 실패 시 시스템 연동
- 콜백 + 결제 상태 확인 API로 연동
- Circuit Breaker / Retry로 장애 확산 방지
- 외부 장애 시 내부 시스템 정상 응답
- 콜백 미수신 시 폴링/수동 API로 복구
- 타임아웃 실패 시 결제 조회 API로 상태 반영

---

## 3. Resilience 흐름 및 전제 조건

### 3.1 전체 흐름

```
Timeout (장애 감지)
  → Retry (일시적 장애 대응)
    → Circuit Breaker (반복 실패 차단)
      → Fallback (차단 상태에서 사용자 경험 보호)
```

### 3.2 전제 조건 (Roadmap §7 연결 고리)

이 전체 흐름이 안전하려면:


| 전제          | 내용                                |
| ----------- | --------------------------------- |
| **트랜잭션 경계** | 외부 호출은 `@Transactional` 밖에 있어야 한다 |
| **멱등성**     | 재시도가 안전하려면 멱등성이 보장되어야 한다          |
| **비동기 결제**  | 비동기 결제 흐름은 상태 머신으로 관리되어야 한다       |


---

## 4. 단계별 기술 선택 및 트레이드오프

### 4.1 Timeout (장애 감지)


| 기술               | 선택                                              | 트레이드오프                                                                                                  |
| ---------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **RestTemplate** | `RestTemplate` + `ClientHttpRequestFactory`     | 장점: 설정 단순, `setConnectTimeout`/`setReadTimeout` 직접 지정. 단점: Bean별 Factory 분리 필요, Spring 6.1+ deprecated. |
| **WebClient**    | `WebClient` + `HttpClient`                      | 장점: 논블로킹. 단점: PG 호출은 동기 패턴이므로 `.block()` 필요.                                                            |
| **Feign**        | `@FeignClient` + `connectTimeout`/`readTimeout` | 장점: 선언적, 타임아웃 설정 간단. 단점: Spring Cloud OpenFeign 의존성 추가.                                                 |


**권장**: RestTemplate 또는 Feign. PG 100~~500ms 지연이면 Read Timeout 1~~2초가 합리적.

**설정 예시 (RestTemplate)**:

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofMillis(500));   // Connection Timeout
factory.setReadTimeout(Duration.ofSeconds(2));      // Read Timeout (PG 100~500ms + 여유)
```

**트레이드오프 (Roadmap §1)**:

- Connection Timeout 없음: 연결 실패 시 무한 대기 → 스레드 고갈
- Read Timeout 없음: PG 응답 지연 시 Tomcat 스레드 고갈 → HikariCP까지 영향
- 너무 짧으면: 정상 요청도 타임아웃 → 실패율 증가

**Phase 2 리스크와 대응 (타임아웃·트랜잭션)**:


| 리스크                         | 문제 요약                                                    | 설계상 대응                                                                                                                                                      | 근거                                                       |
| --------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| **1. 결제 원자성(Read Timeout)** | 타임아웃 시 PG는 성공·우리는 실패 처리 → 망 취소/데이터 불일치                   | 타임아웃 = 실패 확정이 아님. **즉시 실패 처리하지 않음**. (1) Retry로 일시 지연 재시도 (2) Fallback에서 PENDING 유지 (3) Phase 8 폴링/수동 조회로 `GET /payments?orderId=` 호출해 최종 상태 반영             | §2.2 비동기 특성, §6.1 성공 후 응답 유실→콜백+폴링, §11.1 Read Timeout 행 |
| **2. Read 2초 적정성**          | PG·카드사 구간에서 2초 초과 빈번 시 정상 건 대량 타임아웃 → 매출 손실              | 기본값 2초(§2.2 P99≈500ms 기준). **실 PG/카드사 구간이 긴 경우** Read 3~5초로 상향 검토. 풀 고갈 방지는 **Circuit Breaker**(§4.3)로 담당: 반복 실패·Slow Call 시 호출 차단                          | §4.1, §4.3, §12 slowCallDurationThreshold                |
| **3. Connection 500ms 리스크** | DNS/Handshake 지연·해외 PG 시 500ms 부족 가능                     | **Connect 단계 Retry**로 대응. §4.2·§5: `ConnectException`을 Retryable로 지정, 2~3회 재시도(Exponential Backoff + Jitter). 연결만 재시도하므로 스레드 점유는 제한적                        | §11.1 Connection Timeout 행, §4.2 Retryable 목록            |
| **4. DB 트랜잭션 점유**           | PG 호출이 TX 안에 있으면 Read 2초 = DB 커넥션 2초 점유 → 풀 고갈, 타 서비스 마비 | **Phase 2 핵심**: PG 호출을 **반드시 `@Transactional` 밖**으로 분리. (1) 1단계 TX: 주문 검증 + PENDING 저장 후 커밋 (2) TX 종료 후 `self.requestPaymentToPg()` 호출. PG 호출 구간에는 DB TX 없음 | §5.1 표 및 구조 예시, §9 흐름도 4번                                |


---

### 4.2 Retry (일시적 장애 대응)


| 기술                     | 선택                             | 트레이드오프                                                                                   |
| ---------------------- | ------------------------------ | ---------------------------------------------------------------------------------------- |
| **Resilience4j Retry** | `@Retry(name = "pgRetry")`     | 장점: Spring Boot 통합, Exponential Backoff + Jitter. 단점: Aspect 순서(Circuit Breaker와) 설정 필요. |
| **수동 Retry**           | `for`/`while` + `Thread.sleep` | 장점: 제어 용이. 단점: Retry Storm 위험, Jitter 없음.                                                |
| **Spring Retry**       | `@Retryable`                   | 장점: Spring 네이티브. 단점: Resilience4j와 기능 중복.                                                |


**권장**: Resilience4j Retry.

**Retryable vs Non-retryable (Roadmap §5)**:


| 재시도 O (일시적)              | 재시도 X (영구적)        |
| ------------------------ | ------------------ |
| `SocketTimeoutException` | HTTP 400 (잘못된 요청)  |
| `ConnectException`       | HTTP 401 (인증 실패)   |
| HTTP 503 (서버 과부하)        | 비즈니스 에러 ("잘못된 카드") |


**트레이드오프**:

- Fixed Backoff: 단순하지만 과부하 시 비효율
- Exponential + Jitter: Thundering Herd 완화, 회복 시간 확보
- Retry Storm: PG 503 시 모든 클라이언트 재시도 → Circuit Breaker와 조합 필수

---

### 4.3 Circuit Breaker (반복 실패 차단)


| 기술                              | 선택                                    | 트레이드오프                                                           |
| ------------------------------- | ------------------------------------- | ---------------------------------------------------------------- |
| **Resilience4j CircuitBreaker** | `@CircuitBreaker(name = "pgCircuit")` | 장점: Sliding Window, Slow Call, Actuator 연동. 단점: threshold 튜닝 필요. |
| **Spring Cloud CircuitBreaker** | 추상화 레이어                               | 장점: 구현체 교체 용이. 단점: Resilience4j 직접 사용 시 추가 추상화 비용.               |


**권장**: Resilience4j CircuitBreaker.

**트레이드오프 (Roadmap §4)**:

- `failure-rate-threshold` 낮음: 민감하게 Open → 정상 요청도 차단
- `failure-rate-threshold` 높음: 장애 감지 지연
- `slow-call-`*: PG가 느리기만 할 때도 보호 가능
- Open 시: `CallNotPermittedException` → Fallback으로 처리

---

### 4.4 Fallback (차단 상태에서 사용자 경험 보호)


| 기술                              | 선택                                   | 트레이드오프                                                                    |
| ------------------------------- | ------------------------------------ | ------------------------------------------------------------------------- |
| **Resilience4j fallbackMethod** | `fallbackMethod = "paymentFallback"` | 장점: Circuit Breaker/Retry와 동일 메서드로 처리. 단점: Fallback 내부에서 외부 호출/무거운 쿼리 금지. |
| **Controller 레벨 try-catch**     | `@ExceptionHandler`                  | 장점: 전역 처리. 단점: Resilience와 분리되어 일관성 떨어짐.                                  |


**권장**: Resilience4j `fallbackMethod`.

**Fallback 수준 (Roadmap §6)**:


| 수준      | 전략               | 예시                                        |
| ------- | ---------------- | ----------------------------------------- |
| Level 1 | 안내 메시지 반환        | "현재 결제 처리가 지연되고 있습니다"                     |
| Level 2 | PENDING 저장 후 재처리 | 결제를 PENDING으로 저장, 나중에 PG 복구 시 처리 **(권장)** |
| Level 3 | 부분 서비스 유지        | 결제 불가하지만 주문 조회는 가능                        |


**트레이드오프**:

- Fallback에서 외부 호출: 연쇄 장애 위험
- 무거운 DB 쿼리: Fallback 자체가 병목
- PENDING 저장: 가볍고 확실한 작업만 수행

---

## 5. 전제 조건별 구현

### 5.1 트랜잭션 경계 (Roadmap §2)


| 구간         | 트랜잭션                            | 구현                                                    |     |
| ---------- | ------------------------------- | ----------------------------------------------------- | --- |
| 주문 검증      | `@Transactional(readOnly=true)` | `PaymentFacade.requestPayment` 1단계                    |     |
| PENDING 저장 | `@Transactional`                | PaymentModel PENDING 저장 후 커밋                          |     |
| PG 호출      | **트랜잭션 밖**                      | `self.requestPaymentToPg()` (self-injection으로 새 트랜잭션) |     |
| 콜백 처리      | `@Transactional`                | `OrderService.completePayment` 한 트랜잭션                 |     |


**구조 예시**:

```java
// PaymentFacade
@Transactional
public PaymentInfo requestPayment(...) {
    // 1. 주문 검증 (readOnly)
    // 2. PENDING 저장
    // 3. 커밋
}
// 4. 트랜잭션 밖에서 self.requestPaymentToPg() 호출
```

**트레이드오프**:

- PG 호출을 트랜잭션 안에 두면: DB 커넥션 장시간 점유 → 풀 고갈
- 2단계 커밋: PENDING 먼저 커밋 → PG 호출 → 콜백으로 최종 반영

---

### 5.2 멱등성 (Roadmap §7)


| 기술                       | 선택                                  | 트레이드오프                                    |
| ------------------------ | ----------------------------------- | ----------------------------------------- |
| **orderId + PENDING 체크** | 같은 주문에 PENDING 있으면 신규 요청 차단         | 장점: PG 스펙과 맞음. 단점: PENDING 해소 전까지 재시도 불가. |
| **Idempotency-Key (DB)** | `payment_idempotency` 테이블 + UNIQUE  | 장점: 영속성. 단점: TTL, 동시 요청 처리 필요.            |
| **Redis SETNX**          | `SET idempotency:{key} EX 86400 NX` | 장점: 빠름. 단점: Redis 장애 시 유실.                |


**권장**: orderId 기반 + PENDING 중복 차단. 필요 시 Idempotency-Key 추가.

**트레이드오프**:

- 멱등성 없이 Retry: 이중 결제 위험
- 동시 요청: DB UNIQUE 또는 분산 락으로 처리

---

### 5.3 비동기 결제 상태 머신 (Roadmap §3)


| 기술                               | 선택                                | 트레이드오프                            |
| -------------------------------- | --------------------------------- | --------------------------------- |
| **PaymentModel + PaymentStatus** | PENDING, SUCCESS, FAILED, TIMEOUT | 장점: 단순. 단점: 복잡한 전이 규칙은 코드로 직접 제어. |
| **Spring State Machine**         | `@EnableStateMachine`             | 장점: 전이 규칙 명시. 단점: 과한 복잡도.         |
| **enum + 전이 메서드**                | `PaymentStatus.transitionTo(...)` | 장점: 가볍고 명확. 단점: 상태가 많아지면 관리 부담.   |


**권장**: `PaymentStatus` enum + `OrderModel.markPaid()` 등 도메인 메서드로 전이 제어.

---

## 6. 외부 시스템 불확실성 및 상태 설계 (SKILL §1, §3)

### 6.1 불확실성 대응


| 불확실성           | 대응                                 |
| -------------- | ---------------------------------- |
| **지연**         | Connection/Read Timeout 설정 (1~2초)  |
| **실패**         | Retry(재시도 가능 예외) + Circuit Breaker |
| **중복 실행**      | orderId 기반 멱등성, PENDING 중복 요청 차단   |
| **성공 후 응답 유실** | 콜백 + 폴링/수동 조회로 복구                  |


### 6.2 주문·결제 상태 전이

```
[주문] ORDERED (결제 전)
    ↓ 결제 요청 접수
[결제] PAYMENT_PENDING (PG 접수, 콜백 대기)
    ↓ 콜백 성공
[주문] PAID, [결제] PAYMENT_SUCCESS (재고 차감)

    ↓ 콜백 실패 (한도초과/잘못된카드)
[결제] PAYMENT_FAILED

    ↓ 일정 시간 내 콜백 미수신
[결제] PAYMENT_TIMEOUT → 폴링/수동 조회로 복구 시도
```

### 6.3 허용되지 않는 전이

- `PAID → PAYMENT_PENDING` (절대 불가)
- `PAID` 주문에 대한 중복 결제 요청 차단

### 6.4 내부 vs 외부 상태


| 내부 (commerce-api) | 외부 (PG-Simulator)      | 불일치 시점          |
| ----------------- | ---------------------- | --------------- |
| PAYMENT_PENDING   | 접수됨 (transactionId 반환) | 콜백 유실, 타임아웃     |
| PAID              | 결제 완료                  | 내부 커밋 실패, 콜백 중복 |
| PAYMENT_FAILED    | 실패                     | -               |


---

## 7. 구현 단계 (Resilience Roadmap 순서)

### Phase 0: 의존성

- `resilience4j-spring-boot3` (Circuit Breaker, Retry) 추가 (`build.gradle.kts`)
- Spring Cloud OpenFeign (Feign 사용 시) 추가

### Phase 1: Timeout (§1)

- **RestTemplate**: `setConnectTimeout(500ms)`, `setReadTimeout(2000ms)` (PG 100~500ms + 여유)
- **Feign** (선택): `connectTimeout`, `readTimeout` 동일
- 타임아웃 시 `SocketTimeoutException` → Fallback 또는 사용자 안내

**의사결정 근거 (전제 + 결론)**  

- 전제: PG 응답 지연 100~500ms, P99 ≈ 500ms (06 §2.2, mentor-DEVIN §2.1).  
- 결론: Connect 500ms(연결 구간 빠른 실패), Read 2s(P99 포함).  
- 타임아웃 = 실패 확정이 아님 → PENDING 유지 후 폴링/수동 조회로 복구(Phase 8).  
- **운영 참고**: 실 PG·카드사 구간이 긴 환경에서는 Read 3~5초로 상향 검토. 풀 고갈 방지는 Circuit Breaker로 보완(§4.1 표 위 Phase 2 리스크 표).

### Phase 2: 트랜잭션 경계 (§2)

- PG 호출을 `@Transactional` 메서드 밖으로 분리
- PaymentFacade: (1) DB 트랜잭션으로 PENDING 저장 (2) 트랜잭션 종료 후 PG 호출
- 콜백 핸들러: 단일 `@Transactional`로 재고 차감 + PAID 저장

**의사결정 근거 (전제 + 결론)**  

- 전제: PG 호출은 100~500ms 지연·1~5초 비동기 처리 가능(06 §2.2). DB 커넥션을 그동안 점유하면 풀 고갈·장애 확산 위험(06 §5.1, mentor-ALEN §5.2).  
- 결론: PENDING 저장까지 한 TX로 커밋한 뒤, 같은 스레드에서 TX 밖으로 PG 호출. 콜백은 별도 단일 TX로 `completePayment` 처리.  
- **운영 참고**: TX 길이·동시 요청 수 모니터링. PG 호출 실패 시 PENDING만 남고 Fallback(Phase 6) 전까지 사용자 재시도에 의존하므로, Phase 4~6(CB·Retry·Fallback) 적용 후 운영 부담 완화.

### Phase 3: 비동기 결제 흐름 (§3)

- `POST /api/v1/payments/callback` 구현 (PG가 호출)
- 콜백 Body 파싱: paymentId, orderId, success, failureReason 등 PG 스펙 준수
- 성공 시: `OrderService.completePayment(orderId)` → 재고 차감 + `OrderModel.markPaid()`
- 실패 시: PaymentModel PAYMENT_FAILED, 주문은 ORDERED 유지

### Phase 4: Circuit Breaker (§4)

- Resilience4j CircuitBreaker 적용 (PG 호출부)
- 설정: `slidingWindowSize`, `failureRateThreshold`, `slowCallDurationThreshold`, `waitDurationInOpenState`
- Open 시: `CallNotPermittedException` → Fallback (PENDING 저장 + "잠시 후 다시 시도" 응답)
- Actuator `/actuator/circuitbreakers` 모니터링

**Phase 4 구현 시 발생 가능한 문제점(리스크)**

- **잘못된 Open(오탐)으로 인한 매출/전환 손실**
  - 문제 요약: `failure-rate-threshold` 또는 `slowCallDurationThreshold` 튜닝이 공격적으로 잡히면, 정상 요청도 CB Open으로 차단되어 PG 접수가 지연·증가한다.
  - 근거: 외부 시스템은 지연/실패가 섞여 있으며(SKILL §1, §5), CB는 “실패율/느린 호출” 기준으로 차단하므로 임계값 튜닝이 곧 UX에 영향을 준다.

- **타임아웃/느린 호출의 분류가 예상과 다를 수 있음**
  - 문제 요약: `readTimeout`(Phase 1)과 `slowCallDurationThreshold`(Phase 4)가 비슷한 값이면, timeout이 “느린 호출”로 집계되거나 실패율 계산에 포함되는 방식이 직관과 달라질 수 있다.
  - 결과: 회복 전에 회로가 불필요하게 오래 Open될 수 있다.

- **Fallback이 멱등이 아닌 DB 변경을 수행하면 PENDING 중복/경합이 폭증**
  - 문제 요약: CB Open 시 fallback이 “이미 저장된 PENDING을 재사용”하지 않고 새 PENDING을 저장하면, 동일 `orderId`에 대해 중복 레코드/CONFLICT가 연쇄적으로 발생할 수 있다.
  - 근거: SKILL §4(재시도/중복 실행 가정)에서 외부 호출이 실패·재시도될 수 있으며, Phase 7의 “PENDING 1건” 전제가 깨진다.

- **예외 분류(재시도 가능/불가능)와 CB failure 집계가 어긋날 수 있음**
  - 문제 요약: PG가 반환하는 4xx(잘못된 카드/요청)까지 CB failure로 집계되면, 단일 사용자 입력 오류가 전체 요청 차단으로 확대될 수 있다.
  - 결과: “유효하지 않은 요청”이 다수일 때 CB가 Open되어 정상 PG 요청까지 영향을 받을 수 있다.

- **회로차단 적용 범위가 잘못되면(다른 메서드/다른 빈/다른 name) 보호가 작동하지 않음**
  - 문제 요약: CircuitBreaker `name` 불일치(설정 vs 애노테이션)나, CB가 PG 호출부가 아닌 다른 래퍼 메서드를 감싸면 기대한 “Open → Fallback”이 발생하지 않는다.
  - 근거: Phase 4의 핵심은 `CallNotPermittedException`으로 외부 호출을 차단하는 것이므로, 보호 적용이 틀리면 Phase 6 의도(사용자 경험 보호)가 깨진다.

- **CB가 요청 단계만 보호하고 콜백/상태 복구는 독립적으로 남음**
  - 문제 요약: CB Open은 “요청 자체”를 막지만, 이미 PG 접수된 건에 대해 콜백 유실/지연 같은 비동기 상태 불일치는 별도 복구(Phase 8)가 필요하다.
  - 결과: CB로 사용자 요청이 줄어도, 내부가 PENDING인 채로 장시간 남을 수 있다.

### Phase 5: Retry + Backoff (§5)

- Retryable: `SocketTimeoutException`, `ConnectException`, HTTP 503
- Non-retryable: HTTP 400, 401, 비즈니스 에러
- Aspect 순서: `CircuitBreaker( Retry( 실제호출 ) )` (retryAspectOrder > circuitBreakerAspectOrder)
- Exponential Backoff + Jitter 설정

### Phase 6: Fallback (§6)

- Circuit Breaker + Retry 양쪽 `fallbackMethod` 구현
- Fallback: PENDING 저장, "결제 대기" 응답. **외부 호출/무거운 쿼리 금지**
- PG 전체 장애 시에도 commerce-api는 200 응답

### Phase 7: 멱등성 (§7)

- orderId 기반: 같은 주문에 PENDING 결제가 이미 있으면 새 요청 차단
- 콜백 멱등: 이미 PAID인 주문에 대한 콜백 → 200 OK, 재처리 생략
- (선택) Idempotency-Key 헤더로 클라이언트 중복 방지

### Phase 8: 콜백 미수신 복구

- **폴링**: 일정 시간(예: 5분) 지난 PENDING 건을 배치/스케줄러로 PG `GET /payments?orderId=` 조회 후 상태 반영
- **수동 API**: `POST /api-admin/v1/payments/{orderId}/recover` 또는 `GET /api-admin/v1/payments/pending` → 수동 트리거
- 타임아웃으로 PG 요청 실패한 경우: PENDING 저장 후, 동일 복구 메커니즘으로 PG 조회 API 호출

---

## 8. 기술 스택 요약


| 단계                  | 기술                                                                 | 의존성                                                          | 비고                     |
| ------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------ | ---------------------- |
| **PG 호출**           | RestTemplate 또는 Feign                                              | `spring-boot-starter-web` / `spring-cloud-starter-openfeign` | Timeout 설정             |
| **Timeout**         | `ClientHttpRequestFactory` 또는 Feign `connectTimeout`/`readTimeout` | -                                                            | Connect 500ms, Read 2s |
| **Retry**           | Resilience4j Retry                                                 | `resilience4j-spring-boot3`                                  | Exponential + Jitter   |
| **Circuit Breaker** | Resilience4j CircuitBreaker                                        | `resilience4j-spring-boot3`                                  | Slow Call 포함           |
| **Fallback**        | Resilience4j `fallbackMethod`                                      | -                                                            | PENDING 저장             |
| **멱등성**             | orderId + DB `Payment`                                             | -                                                            | PENDING 중복 차단          |
| **상태 머신**           | `PaymentStatus` enum + 도메인 메서드                                     | -                                                            | 전이 규칙 코드화              |


---

## 9. 결제 API 구현 흐름

```
[Client] POST /api/v1/payments { orderId, cardType, cardNo }
    ↓
[PaymentFacade] 1. 주문 검증 (ORDERED, 본인) — readOnly TX
    2. PENDING 중복 체크 (멱등성)
    3. PaymentModel PENDING 저장 — TX 커밋
    4. (TX 밖) PgSimulatorClient.requestPayment()
       ← @CircuitBreaker @Retry 적용, Timeout 설정
       ← 실패 시 fallbackMethod: PENDING 유지, "결제 대기" 응답
    ↓
[PG-Simulator] 1~5초 후 POST /api/v1/payments/callback
    ↓
[PaymentFacade.handleCallback] 5. OrderService.completePayment(orderId) — TX
    (재고 차감 + OrderModel.markPaid) — 멱등: 이미 PAID면 스킵
```

---

## 10. 도메인·레이어별 구현 항목

### 10.1 Domain


| 항목                | 내용                                                                          |
| ----------------- | --------------------------------------------------------------------------- |
| OrderModel        | `markPaid()` (ORDERED → PAID)                                               |
| OrderStatus       | 기존 PAID 활용                                                                  |
| ProductModel      | `decreaseStock(Quantity)` (음수 방지)                                           |
| ProductService    | `decreaseStock(List<DecreaseStockItem>)` (productId 오름차순 락)                 |
| OrderService      | `completePayment(orderId)` (재고 차감 + markPaid, 멱등)                           |
| PaymentModel (신규) | orderId, pgTransactionId, status(PENDING/SUCCESS/FAILED/TIMEOUT), createdAt |
| PaymentRepository | interface (Domain), impl (Infrastructure)                                   |


### 10.2 Application


| 항목            | 내용                                                                         |
| ------------- | -------------------------------------------------------------------------- |
| PaymentFacade | `requestPayment(userId, orderId, cardType, cardNo)`, `handleCallback(...)` |
| PaymentFacade | 트랜잭션 분리: PENDING 저장 → (밖에서) PG 호출                                          |
| PaymentFacade | Fallback: PENDING 저장, 경량 응답                                                |


### 10.3 Infrastructure


| 항목                   | 내용                                                                                    |
| -------------------- | ------------------------------------------------------------------------------------- |
| PgSimulatorClient    | RestTemplate 또는 FeignClient, Timeout 설정                                               |
| PgSimulatorClient    | `requestPayment(...)`, `getPaymentStatus(paymentId)`, `getPaymentsByOrderId(orderId)` |
| PaymentJpaRepository | PaymentModel persistence                                                              |


### 10.4 Interfaces


| 항목                  | 내용                                                      |
| ------------------- | ------------------------------------------------------- |
| PaymentV1Controller | `POST /api/v1/payments` (requestPayment)                |
| PaymentV1Controller | `POST /api/v1/payments/callback` (PG 콜백, 인증 정책 결정)      |
| PaymentV1Dto        | Request/Response DTO                                    |
| (Admin)             | `POST /api-admin/v1/payments/recover` 또는 수동 복구 API (선택) |


---

## 11. 장애 시나리오 (SKILL §5)

각 시나리오별로 **발생 원인**, **데이터 정합성**, **상태 불일치**, **대응/복구**를 정리한다.

### 11.1 결제 요청 단계 (Client → commerce-api → PG)


| 시나리오                         | 발생 원인                  | 데이터 정합성                | 상태 불일치               | 대응/복구                                                                                                                                  |
| ---------------------------- | ---------------------- | ---------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Connection Timeout**       | PG 서버 다운, 네트워크 끊김, 방화벽 | PENDING 저장됨 (Fallback) | 없음                   | Retry(ConnectException) → Fallback → PENDING 유지. 폴링/수동 조회로 PG 복구 후 확인.                                                                 |
| **Read Timeout**             | PG 응답 지연(100~500ms 초과) | PENDING 저장됨            | **PG는 접수 성공했을 수 있음** | Retry → Fallback. **타임아웃 = 실패가 아님**. 폴링 시 PG `GET /payments?orderId=`로 실제 접수 여부 확인 후 반영.                                               |
| **PG 40% 요청 실패**             | PG 접수 거부(일시적 과부하 등)    | PENDING 저장됨            | 없음                   | Retry(HTTP 5xx) → Fallback. 사용자 "결제 대기" 안내. 폴링으로 재시도 또는 사용자 재요청.                                                                       |
| **Retry 전부 실패**              | 3회 재시도 후에도 실패          | PENDING 저장됨            | 없음                   | Fallback 실행. PENDING 유지, "잠시 후 다시 시도해주세요" 응답.                                                                                          |
| **Circuit Breaker Open**     | 최근 N건 중 실패율/느린 호출 초과   | PENDING 저장됨            | 없음                   | PG 호출 생략, Fallback 즉시. `CallNotPermittedException` → PENDING 저장. Half-Open 후 재시도.                                                      |
| **PENDING 저장 전 DB 실패**       | 주문 검증/저장 중 예외          | 롤백, 저장 안 됨             | 없음                   | 클라이언트에 5xx 또는 BAD_REQUEST 반환. 사용자 재요청.                                                                                                 |
| **PENDING 저장 후 PG 호출 전 크래시** | 프로세스/컨테이너 종료           | PENDING만 저장, PG 미호출    | 내부 PENDING / PG 무상태  | 재기동 후: PENDING 건에 대해 PG 조회 API로 "접수 여부" 확인 불가(요청 자체가 안 나감). **폴링은 orderId로 PG 조회** → PG에 없으면 "미접수"로 판단, PAYMENT_TIMEOUT 또는 사용자 재요청 안내. |
| **동시에 같은 주문 결제 요청**          | 사용자 중복 클릭, 로드밸런서 재시도   | 첫 요청만 PENDING, 둘째는 차단  | 없음                   | orderId + PENDING 존재 시 `CONFLICT` 반환. "이미 결제 진행 중" 안내.                                                                                 |


### 11.2 PG 내부 처리 단계 (PG 접수 → 처리 1~5초)


| 시나리오                        | 발생 원인         | 데이터 정합성                                   | 상태 불일치               | 대응/복구                                                                                                    |
| --------------------------- | ------------- | ----------------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------- |
| **PG 처리 결과: 한도 초과(20%)**    | 카드 한도 초과      | PENDING → PAYMENT_FAILED                  | 없음                   | 콜백으로 `success=false, reason=LIMIT_EXCEEDED` 수신. PaymentModel FAILED, 주문 ORDERED 유지. 사용자 재결제 또는 다른 카드 안내. |
| **PG 처리 결과: 잘못된 카드(10%)**   | 카드 번호/유효기간 오류 | PENDING → PAYMENT_FAILED                  | 없음                   | 콜백으로 `success=false, reason=INVALID_CARD` 수신. PaymentModel FAILED. 사용자 카드 정보 수정 안내.                      |
| **PG 처리 결과: 성공(70%)**       | 정상 결제         | PENDING → PAYMENT_SUCCESS, ORDERED → PAID | 없음                   | 콜백 수신 → completePayment → 재고 차감 + PAID.                                                                  |
| **PG 접수 성공 but 처리 중 PG 장애** | PG 내부 크래시     | PG 측 불명                                   | PG "접수됨" / 실제 처리 미완료 | 콜백이 오지 않을 수 있음. 폴링 시 PG 조회 API 응답에 따라 SUCCESS/FAILED/미확인 처리. PG 측 복구 정책에 의존.                             |


### 11.3 콜백 단계 (PG → commerce-api callback)


| 시나리오                                  | 발생 원인                               | 데이터 정합성     | 상태 불일치                 | 대응/복구                                                                                               |
| ------------------------------------- | ----------------------------------- | ----------- | ---------------------- | --------------------------------------------------------------------------------------------------- |
| **콜백 유실**                             | 네트워크 끊김, commerce-api 다운, PG 재시도 포기 | PAID 미반영    | **PG 성공 / 내부 PENDING** | 폴링: 일정 시간(예: 5분) 지난 PENDING 건을 PG `GET /payments?orderId=` 조회. PG가 SUCCESS면 completePayment 호출.     |
| **콜백 지연**                             | PG 처리 5초 초과, 네트워크 지연                | PENDING 유지  | 일시적                    | 사용자는 "결제 대기" 상태. 콜백 도착 시 정상 처리. 별도 대응 불필요.                                                          |
| **콜백 중복**                             | PG 재시도, 로드밸런서 중복 전달                 | 이중 재고 차감 위험 | -                      | **멱등성**: completePayment 진입 시 주문 상태 확인. 이미 PAID면 재고 차감/상태 변경 생략, 200 OK 반환.                         |
| **콜백 수신 시 completePayment 실패(재고 부족)** | 결제 완료 시점에 재고 소진                     | 재고/주문 롤백    | PG 성공 / 내부 실패          | TX 롤백. 주문은 ORDERED 유지, Payment는 PENDING 또는 별도 FAILED. **보상**: PG 결제 취소 API 호출(있는 경우). 없으면 운영 수동 처리. |
| **콜백 수신 시 completePayment 실패(DB 에러)** | DB 연결 끊김, 데드락 등                     | 롤백          | PG 성공 / 내부 실패          | TX 롤백. 콜백 재호출 또는 폴링으로 재시도. 멱등성으로 이중 적용 방지.                                                          |
| **콜백 Body 파싱 실패**                     | PG 스펙 변경, 잘못된 JSON                  | 처리 중단       | -                      | 4xx 반환(또는 로그 후 200). PG 재시도 가능. 파싱 실패 건은 수동 확인.                                                     |
| **위조/비정상 콜백**                         | 악의적 요청, 잘못된 호출                      | -           | -                      | IP 화이트리스트, 시크릿 검증 등. 검증 실패 시 403, 처리 생략.                                                            |


### 11.4 복구(폴링/수동) 단계


| 시나리오                    | 발생 원인                       | 데이터 정합성             | 상태 불일치                                     | 대응/복구                                                           |
| ----------------------- | --------------------------- | ------------------- | ------------------------------------------ | --------------------------------------------------------------- |
| **폴링 시 PG 조회 API 타임아웃** | PG 장애 지속                    | PENDING 유지          | -                                          | 다음 폴링 주기에서 재시도. Circuit Breaker 적용 시 PG 호출 차단되면 스킵.             |
| **폴링 시 PG "미접수" 응답**    | PENDING인데 PG에 해당 orderId 없음 | 내부 PENDING / PG 무기록 | 요청이 PG에 도달하지 않음(Connection/Read Timeout 등) | PAYMENT_TIMEOUT 또는 PAYMENT_FAILED로 갱신. 사용자 "결제 실패, 재시도해주세요" 안내. |
| **폴링 시 PG "접수됨, 처리 중"** | PG가 아직 처리 안 함               | PENDING 유지          | -                                          | 다음 폴링에서 재확인.                                                    |
| **수동 복구 API 호출**        | 운영자가 특정 주문 강제 복구            | -                   | -                                          | PG 조회 후 상태 반영. 남용 방지(권한, 로그).                                   |


### 11.5 주문/상품 도메인 관련


| 시나리오                         | 발생 원인                 | 데이터 정합성 | 상태 불일치 | 대응/복구                                                                                                               |
| ---------------------------- | --------------------- | ------- | ------ | ------------------------------------------------------------------------------------------------------------------- |
| **결제 요청 시 주문이 이미 CANCELLED** | 사용자가 주문 취소 후 결제 시도    | -       | -      | 주문 검증 단계에서 `BAD_REQUEST` 또는 `CONFLICT`. "취소된 주문입니다" 안내.                                                             |
| **결제 요청 시 주문이 이미 PAID**      | 중복 결제 시도, 콜백 먼저 도착    | -       | -      | PENDING 중복 체크 또는 주문 상태 확인. `CONFLICT` "이미 결제 완료" 반환.                                                                |
| **재고 차감 시 상품 삭제됨**           | 어드민이 상품 삭제            | 복구 불가   | -      | `completePayment`에서 `ProductService.decreaseStock` 시 NOT_FOUND. TX 롤백. 운영 수동: 주문 취소 + PG 환불 또는 재고 수동 조정.            |
| **주문 취소와 결제 완료 동시**          | 사용자 취소 요청 vs 콜백 수신 경쟁 | -       | -      | `completePayment`와 `cancel` 모두 주문 상태 검증. PAID로 전이된 뒤 cancel 요청 시 "결제 완료 건은 재고 복구 후 취소" (기존 OrderService.cancel 로직). |


### 11.6 요약: 시나리오별 우선 대응


| 구간              | 핵심 리스크        | 필수 대응                                                    |
| --------------- | ------------- | -------------------------------------------------------- |
| PG 요청           | 타임아웃 = 실패가 아님 | Timeout + Retry + Fallback, PENDING 저장, 폴링으로 PG 실제 상태 확인 |
| 콜백              | 유실, 중복        | 폴링/수동 복구, 멱등성(이미 PAID면 스킵)                               |
| completePayment | 재고 부족, DB 에러  | TX 롤백, PG 취소(가능 시), 폴링 재시도                               |
| 동시성             | 같은 주문 중복 요청   | orderId + PENDING 체크, CONFLICT 반환                        |


### 11.7 보안 및 데이터 변조 (Security)

클라이언트나 외부 공격자가 결제 관련 데이터를 조작하는 경우. **현재 프로젝트 적용 가능**: 결제 금액은 클라이언트가 보내지 않고, 서버에서 주문 금액을 계산해 PG에 전달하는 구조로 설계하면 됨. 콜백 수신 시 금액 대조는 구현 대상.


| 시나리오                              | 발생 원인                           | 데이터 정합성                               | 상태 불일치 | 대응/복구                                                                                                                                                                                                       |
| --------------------------------- | ------------------------------- | ------------------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **결제 금액 변조 (Price Manipulation)** | 클라이언트가 요청을 조작해 PG에는 낮은 금액 전달 시도 | PG: 100원 결제 / DB: 10,000원 주문 시 금전적 손실 | 금액 불일치 | **요청 단계**: 금액을 클라이언트가 보내지 않음. 서버에서 주문(orderId) 기준으로 `sum(priceSnapshot * quantity)` 계산 후 PG에 전달. **콜백 단계**: 콜백 수신 시 PG의 결제 금액과 DB 주문 금액을 반드시 대조. 불일치 시 completePayment 수행하지 않고, 로그/알림 후 수동 검토(필요 시 PG 망취소). |


### 11.8 인프라 및 설정 (Infrastructure) — 실 PG 연동/운영 시

코드 로직 외 인프라·설정 문제. PG-Simulator 과제에서는 해당 사항이 적으나, **실 PG 연동 시** 고려할 시나리오.


| 시나리오                       | 발생 원인                            | 데이터 정합성  | 상태 불일치             | 대응/복구                                                                          |
| -------------------------- | -------------------------------- | -------- | ------------------ | ------------------------------------------------------------------------------ |
| **PG API Key / 인증서 만료**    | 운영 관리 실수, 인증 토큰 갱신 실패            | 전체 결제 불가 | 없음                 | 401(Unauthorized) 모니터링 및 긴급 알림(Slack 등). Circuit Breaker 작동 시 사용자에게 "점검 중" 안내. |
| **Webhook 도메인 화이트리스트 미등록** | 인프라 변경(L4/방화벽), 신규 PG 도입 시 설정 누락 | 콜백 수신 불가 | PG 성공 / 내부 PENDING | 스테이징에서 콜백 수신 검증 필수. 장애 시 수동 폴링 및 방화벽/화이트리스트 설정 복구.                             |


### 11.9 참고: 현재 범위 외 시나리오

아래는 **현재 프로젝트 범위에는 포함하지 않으나**, 실 서비스 또는 기능 확장 시 검토할 수 있는 항목이다.


| 구분                | 시나리오                       | 현재 프로젝트 적용 여부 | 사유                                                                                                                                                                 |
| ----------------- | -------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **C. 비즈니스 예외**    | 쿠폰/포인트 선점 후 결제 실패 시 복구     | **미적용**       | 쿠폰·포인트는 요구사항에서 "범위 제외"(`.docs/design/01-requirements.md` §5)로 정의되어 있어, 현재 결제 플로우에 쿠폰/포인트가 없음. 해당 기능 도입 시에 한해 "결제 최종 실패(PAYMENT_FAILED) 시 쿠폰/포인트 복구" 정책을 별도 설계하면 됨. |
| **D. 정산 및 사후 검증** | 일일 정산 불일치(Settlement Miss) | **미적용**       | 현재는 PG-Simulator 기반 과제이며, 일일 정산·PG 정산 API·매출 대조 배치가 스코프에 없음. 실 서비스 전환 시 정산 배치와 불일치 알림을 별도 단계에서 설계하는 것이 적절함.                                                        |


---

## 12. 설정 예시 (Resilience4j)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgCircuit:
        sliding-window-size: 10
        failure-rate-threshold: 50
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 10s
    circuitBreakerAspectOrder: 1
  retry:
    instances:
      pgRetry:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        enable-randomized-wait: true
        randomized-wait-factor: 0.5
    retryAspectOrder: 2
```

---

## 13. 참고 문서


| 구분                 | 링크/경로                                                                      |
| ------------------ | -------------------------------------------------------------------------- |
| Resilience 학습 로드맵  | `resilience-learning-roadmap.md`                                           |
| 외부 연동 분석 스킬        | `skills/analize_external_integration/SKILL.md`                             |
| Resilience4j 공식    | [https://resilience4j.readme.io/](https://resilience4j.readme.io/)         |
| Stripe Idempotency | [https://stripe.com/blog/idempotency](https://stripe.com/blog/idempotency) |
| 주문 요구사항            | `.docs/design/01-requirements.md`                                          |
| 주문 상태/ERD          | `.docs/design/04-erd.md`                                                   |
| 결제 변경 이슈·테스트 근거 | `.docs/design/06-payment-change-issues.md`                                 |


---

## 14. 테스트 설계 (Phase별)

> 테스트 설계 근거: `06-payment-change-issues.md`. TDD 원칙에 따라 Unit → Integration → E2E 순으로 작성한다.  
> 네이밍: `{메서드명}_{테스트조건}_{예상결과}`.

### Phase 0: 의존성

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Context | `CommerceApiContextTest` | 결제·Feign·Resilience4j 빈 포함 Spring Boot 컨텍스트 로드 성공 |

### Phase 1: Timeout

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration | `PaymentFeignTimeoutPropertiesIntegrationTest.feignDefaultConfig_shouldBindConnectAndReadTimeouts` | `application.yml`에 `connectTimeout: 500`, `readTimeout: 2000` 바인딩 검증 |
| E2E (Phase 6 적용 후) | `requestPayment_whenPgTimeout_shouldReturn200WithPendingMessage` | PG 호출 타임아웃 시 5xx가 아닌 200 + "결제 대기" 응답, DB에는 PENDING 1건 존재 (change-issues §3.1) |

### Phase 2: 트랜잭션 경계

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration | `PaymentPersistenceService.savePendingAndGetRequestParam_afterCommit_paymentPersisted` | PENDING 저장 후 커밋되어 DB에 반영됨. 반환 `PendingPaymentResult`에 `requestParam.amount` = 주문 `finalAmount` (금액은 §1.2 정책에 따라 정수) |
| Integration | `PaymentFacade.requestPayment_afterPersistenceCommit_callsPgClientOutsideTx` | PENDING 저장 TX 커밋 후에만 `PgSimulatorClient.requestPayment` 호출됨 (spy/verify 또는 트랜잭션 경계 검증) |

### Phase 3: 비동기 결제 흐름

#### Unit (Domain)

| 테스트 | 내용 | 근거 |
|--------|------|------|
| `PaymentModel.createPending_withNullOrderId_shouldThrowIAE` | `orderId == null` 시 `IllegalArgumentException` | 도메인 불변식 |
| `PaymentModel.createPending_withValidOrderId_shouldReturnPENDING` | 유효 orderId 시 상태 PENDING, pgTransactionId null | §10.1 |
| `PaymentModel.markSuccess_whenPENDING_shouldSetSUCCESSAndPgTransactionId` | PENDING일 때만 `markSuccess(txId)` 호출 시 SUCCESS + pgTransactionId 설정 | change-issues §1.1 |
| `PaymentModel.markSuccess_whenNotPENDING_shouldNotChangeStatus` 또는 `shouldThrow` | 이미 SUCCESS/FAILED/TIMEOUT인 결제에 `markSuccess` 호출 시 전이하지 않거나 예외 (구현 정책에 따라) | §1.1 상태 전이 검증 |
| `PaymentModel.markFailed_whenPENDING_shouldSetFAILED` | PENDING일 때만 FAILED로 전이 | §1.1 |
| `PaymentModel.markFailed_whenNotPENDING_shouldNotChangeStatus` 또는 `shouldThrow` | 비 PENDING에 `markFailed` 호출 시 덮어쓰기 방지 | §1.1 |
| `PaymentModel.markTimeout_whenPENDING_shouldSetTIMEOUT` | PENDING일 때만 TIMEOUT 전이 | §1.1 |
| `PaymentModel.markTimeout_whenNotPENDING_shouldNotChangeStatus` 또는 `shouldThrow` | 비 PENDING에 `markTimeout` 호출 시 덮어쓰기 방지 | §1.1 |
| `PaymentModel.isPending_accordingToStatus_returnsCorrectly` | status별 isPending() 기대값 | - |
| `PaymentRequestParam.of_withNullFinalAmount_shouldThrowIAE` | `finalAmount == null` 시 예외 | - |
| `PaymentRequestParam.of_withDecimalAmount_shouldApplyPolicy` | 소수점 포함 금액 시 long 변환 정책(절사/반올림) 검증. 정수 원만 사용한다면 문서화 후 경계값 테스트 | change-issues §1.2 |

#### Integration (Application / Domain+DB)

| 테스트 | 내용 | 근거 |
|--------|------|------|
| `PaymentPersistenceService.savePending_withOrderORDEREDAndNoPENDING_shouldSaveAndReturnParam` | 주문 ORDERED, 해당 주문 PENDING 없음 → PENDING 1건 저장, `PendingPaymentResult` 반환 | §2, §7 |
| `PaymentPersistenceService.savePending_withNonExistentOrder_shouldThrowNOT_FOUND` | 존재하지 않는 orderId → CoreException(NOT_FOUND) | §11.1 |
| `PaymentPersistenceService.savePending_whenOrderNotORDERED_shouldThrowBAD_REQUEST` | 주문이 CANCELLED/PAID 등 → BAD_REQUEST "결제할 수 없는 주문 상태" | change-issues, §11.5 |
| `PaymentPersistenceService.savePending_whenPENDINGAlreadyExists_shouldThrowCONFLICT` | 같은 orderId에 PENDING 이미 있음 → CONFLICT "이미 결제 진행 중" | §7, §11.1 |
| `PaymentFacade.handleCallback_whenSuccess_shouldCompletePaymentAndMarkSuccess` | success=true 콜백 시 `completePayment` 호출, 주문 PAID, 재고 차감, Payment SUCCESS + pgTransactionId 반영 | §3, §10.1 |
| `PaymentFacade.handleCallback_whenFailure_shouldMarkFailedAndOrderRemainsORDERED` | success=false 콜백 시 Payment FAILED, 주문 ORDERED 유지, 재고 차감 없음 | §3 |
| `PaymentFacade.handleCallback_whenSuccessIdempotent_shouldNotDoubleSpend` | 이미 PAID인 주문에 success 콜백 재수신 시 재고 추가 차감 없음, 200 OK (멱등) | §7, §11.3, change-issues §3.2 |
| `PaymentFacade.handleCallback_orderOfOperations_pendingFetchedBeforeCompletePayment` | PENDING 결제를 먼저 조회한 뒤, 존재할 때만 completePayment → markSuccess 순서로 처리 (동작 검증 또는 주석/문서 일치) | change-issues §3.2 |

#### E2E (API)

| 테스트 | 내용 | 근거 |
|--------|------|------|
| `requestPayment_withValidRequestAndLogin_shouldReturn200WithPENDING` | POST /api/v1/payments, 로그인+유효 주문 → 200, data.status PENDING, paymentId/orderId 반환 | §2.1 |
| `requestPayment_withoutLogin_shouldReturn401` | X-Loopers-LoginId 없음 → 401 | API 스펙 |
| `requestPayment_withNonExistentOrder_shouldReturn404` | 타인 주문 또는 없는 orderId → 404 | §11.1 — `PaymentV1ApiE2ETest` |
| `requestPayment_whenOrderNotORDERED_shouldReturn400` | 주문 이미 PAID/CANCELLED → 400 | §11.5 — `PaymentV1ApiE2ETest`(취소 후 결제 시도) |
| `paymentCallback_withSuccess_shouldReturn200AndOrderPaid` | POST /callback success=true → 200, 주문 PAID, 재고 차감 반영 (주문 조회/상품 재고로 검증) | §3 |
| `paymentCallback_withFailure_shouldReturn200AndPaymentFAILED` | POST /callback success=false → 200, Payment FAILED, 주문 ORDERED | §3 |
| `paymentCallback_idempotent_whenAlreadyPaid_shouldReturn200WithoutDoubleDeduction` | 동일 주문에 성공 콜백 2회 → 200 두 번, 재고는 1회만 차감 | §7 — `PaymentV1ApiE2ETest` |

### Phase 4: Circuit Breaker

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration / E2E | `requestPayment_whenCircuitOpen_shouldReturn200Fallback` | CB Open 시 PG 호출 없이 Fallback 응답(200, "결제 대기"), PENDING은 이미 저장된 상태 유지 |

#### Phase 4 리스크별 테스트 매핑

| 리스크 | 테스트(제안) | 계층 | 검증 포인트(합격 기준) |
|--------|--------------|------|------------------------|
| 잘못된 Open(오탐) | `requestPayment_whenFailureRateBelowThreshold_shouldKeepCircuitClosed` | Integration | 실패율이 threshold 미만이면 Open 전이되지 않고 PG 호출이 계속 수행된다. |
| 잘못된 Open(오탐) | `requestPayment_whenSlowCallRateBelowThreshold_shouldKeepCircuitClosed` | Integration | 느린 호출 비율이 임계값 미만이면 Open되지 않는다. |
| timeout/slow-call 분류 혼선 | `requestPayment_whenReadTimeoutAndSlowThresholdClose_shouldOpenAsConfigured` | Integration | `readTimeout`/`slowCallDurationThreshold` 조합에서 기대한 카운팅(실패율/느린호출율)으로만 Open 전이된다. |
| fallback 비멱등 DB 변경 | `requestPayment_whenCircuitOpen_shouldNotCreateAdditionalPending` | Integration | 같은 `orderId` 재요청(회로 Open 상태)에서도 PENDING 레코드가 추가 생성되지 않고 기존 1건만 유지된다. |
| 예외 분류/집계 어긋남 | `requestPayment_whenPgReturns400_shouldNotCountAsCircuitFailure` | Integration | 정책상 Non-retryable/Non-failure로 분류한 4xx가 회로 Open을 유발하지 않는다. |
| 예외 분류/집계 어긋남 | `requestPayment_whenPgReturns503Repeated_shouldOpenCircuit` | Integration | 5xx/네트워크 장애가 누적되면 설정값대로 Open 전이된다. |
| 적용 범위(name/빈) 오류 | `requestPayment_withConfiguredCircuitName_shouldTriggerFallbackOnOpen` | Integration | 설정 파일의 CB name과 애노테이션 name이 일치할 때만 `CallNotPermittedException -> fallback` 경로가 동작한다. |
| 요청 단계만 보호(복구 별도) | `requestPayment_whenCircuitOpen_pendingShouldBeRecoverableByPhase8Flow` | Integration (Phase8 연계) | CB Open으로 대기 상태가 된 건이 Phase8 폴링/수동 복구로 최종 상태(SUCCESS/FAILED/TIMEOUT)로 수렴한다. |

> 구현 메모: 위 테스트는 대부분 WireMock/Stub PG + Resilience4j test config(작은 window/낮은 threshold)로 재현 가능하다.  
> 동시성 포함 케이스는 `CountDownLatch` 기반으로 PENDING row count(=1)를 함께 검증한다.

#### Phase 4 현재 적용 대안 + 구현 완료 테스트

| 리스크 | 적용 대안(현재 코드) | 구현 테스트 |
|--------|----------------------|-------------|
| 적용 범위(name/빈) 오류로 CB 미작동 | PG 호출을 `PaymentFacade` 직접 호출에서 분리하여 `PgPaymentRequester`에 `@CircuitBreaker(name=\"pgCircuit\")` 적용 | `PaymentFacadeCircuitBreakerIntegrationTest.requestPayment_whenCircuitBreakerClosed_shouldCallPgOnce` |
| Circuit Open 시 PG 호출이 계속 나가는 문제 | `PgPaymentRequester` fallback(`pgCircuitFallback`)으로 Open 상태에서 외부 호출 차단 및 예외 비전파 | `PaymentFacadeCircuitBreakerIntegrationTest.requestPayment_whenCircuitBreakerOpen_shouldSkipPgCallAndReturnPending` |
| Open 상태 재요청 시 fallback 부작용(중복 진행/경합) | fallback에서 신규 저장 로직 없음 + 기존 `savePendingAndGetRequestParam`의 `PENDING` 중복 차단(CONFLICT) 유지 | `PaymentFacadeCircuitBreakerIntegrationTest.requestPayment_whenCircuitBreakerOpenAndDuplicateOrder_shouldThrowConflict` |
| 테스트 간 회로 상태 누수 | `@AfterEach`에서 `pgCircuit`를 `CLOSED`로 복원 | `PaymentFacadeCircuitBreakerIntegrationTest`, `PaymentFacadeRequestPaymentIntegrationTest` |

### Phase 5: Retry + Backoff

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration | `requestPayment_whenPgReturns503_shouldRetryUpToConfiguredAttempts` | PG 503 시 Retry 설정 횟수만큼 재시도 후 Fallback 또는 실패 응답 |
| Integration | `requestPayment_whenPgReturns400_shouldNotRetry` | PG 400 등 Non-retryable 시 재시도 없이 실패 또는 Fallback (정책에 따라) |

### Phase 6: Fallback

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration / E2E | `requestPayment_whenPgThrows_shouldReturn200WithPendingMessage` | PG 호출 예외(타임아웃/5xx/연결 실패) 시 5xx가 아닌 200 + "결제 대기" 응답, DB PENDING 1건 (change-issues §3.1) |
| Integration / E2E | `requestPayment_whenCircuitOpen_shouldReturn200Fallback` | Phase 4와 동일 — CB Open 시 Fallback 200 |

### Phase 7: 멱등성

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Integration | `savePending_concurrentSameOrder_onlyOnePENDINGOrCONFLICT` | 동일 orderId에 대해 동시에 savePending 요청 시 PENDING 1건만 허용, 나머지 CONFLICT 또는 락 대기 후 CONFLICT (change-issues §2.1 — 주문 락/유니크 적용 후 검증) |
| E2E | `requestPayment_duplicateSameOrder_shouldReturn409` | 같은 주문으로 결제 요청 2회(순차) → 1회 200, 2회 409 "이미 결제 진행 중" |
| Integration | `handleCallback_whenOrderAlreadyPAID_shouldSkipCompletePayment` | 이미 PAID인 주문에 대해 handleCallback(success) 호출 시 completePayment 내부에서 스킵, Payment 상태 덮어쓰기 없음 (Phase 3 테스트와 연계) |

### Phase 8: 콜백 미수신 복구

| 구분 | 테스트 | 내용 |
|------|--------|------|
| Unit/Integration | `PgSimulatorClient_getPaymentsByOrderId_returnType` | `getPaymentStatus`, `getPaymentsByOrderId` 반환 타입이 Object가 아닌 구체 DTO로 변경된 경우 타입 검증 (change-issues §5.2) |
| Integration | `recoverOrPoll_whenPgReturnsSuccess_shouldReflectCompletePayment` | `PaymentFacade.recoverPendingFromPgSimulator` + `PaymentRecoverPollIntegrationTest` — PG `getPaymentsByOrderId` SUCCESS 시 `handleCallback` 경유 반영 |
| Integration | `recoverOrPoll_whenPgReturnsNotAccepted_shouldMarkTimeoutOrFailed` | [x] PG 응답 `null` → 최신 PENDING `TIMEOUT` (`timeoutPendingPaymentForOrder`). 조회 **예외**는 스왈로·PENDING 유지 (`recoverOrPoll_whenPgThrows_shouldSwallowAndKeepPending`) |

### 보안 (change-issues §4)

| 구분 | 테스트 | 내용 |
|------|--------|------|
| E2E | `PaymentV1PaymentCallbackSecretE2ETest` | 시크릿 실패 시 **HTTP 401** (`ErrorType.UNAUTHORIZED`). 문서상 403과 다르면 `ErrorType`/핸들러 조정 검토 (change-issues §4.1) |
| Integration (구현 후) | `handleCallback_whenAmountMismatch_shouldNotCompletePayment` | 콜백 금액 ≠ Order.finalAmount 시 completePayment 호출하지 않음, 로그/알림 등 (change-issues §4.2, §11.7) |

### 스펙·DTO (change-issues §5.1)

| 구분 | 테스트 | 내용 |
|------|--------|------|
| 문서/매핑 | paymentId vs pgTransactionId | PG 스펙과 DTO·도메인 필드 매핑 문서화; PG-Simulator가 paymentId만 주는 경우 현재 매핑이 의도와 일치하는지 검증 |

---


# Phase 4: Retry + Backoff

## 학습 목표
- Retryable vs Non-retryable 예외를 구분하는 기준을 세운다
- Exponential Backoff + Jitter의 필요성을 Retry Storm 관점에서 설명한다
- Retry와 Circuit Breaker의 조합 순서(Aspect Order)를 이해한다

## Aspect 순서: CircuitBreaker( Retry( 실제호출 ) )

```
요청 → [CircuitBreaker] → [Retry] → PG 호출
         (바깥, order=1)    (안쪽, order=2)
```

### 왜 이 순서인가?

**잘못된 순서: Retry( CircuitBreaker( 실제호출 ) )**
```
1. Retry 1차 시도 → CircuitBreaker OPEN → CallNotPermittedException
2. Retry 2차 시도 → CircuitBreaker 여전히 OPEN → CallNotPermittedException
3. Retry 3차 시도 → CircuitBreaker 여전히 OPEN → CallNotPermittedException
→ 3번 다 무의미한 재시도. 자원만 낭비.
```

**올바른 순서: CircuitBreaker( Retry( 실제호출 ) )**
```
Case A: CircuitBreaker CLOSED일 때
  1. Retry 1차 → PG 실패
  2. Retry 2차 → PG 성공 ✅
  → CircuitBreaker에는 "성공 1건"으로 기록

Case B: CircuitBreaker OPEN일 때
  → 즉시 CallNotPermittedException (Retry 시도 자체 안 함)
  → 자원 절약!
```

### 설정

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1  # 낮은 값 = 바깥
  retry:
    retryAspectOrder: 2           # 높은 값 = 안쪽
```

## Exponential Backoff + Jitter

### 왜 필요한가?

**Fixed Backoff (1초 → 1초 → 1초)**
- PG 장애 시 모든 클라이언트가 정확히 1초 후 동시 재시도
- Thundering Herd: 한꺼번에 몰려서 PG가 더 죽음

**Exponential Backoff (1초 → 2초 → 4초)**
- 서버에 회복 시간을 점점 더 줌
- 하지만 여전히 모든 클라이언트가 같은 시점에 재시도

**Exponential Backoff + Jitter (0.5~1.5초 → 1~3초 → 2~6초)**
- 재시도 시점을 랜덤으로 분산
- Thundering Herd 방지

### 현재 설정

```yaml
pgRetry:
  max-attempts: 3                   # 최초 1회 + 재시도 2회
  wait-duration: 1s                 # 기본 대기
  enable-exponential-backoff: true  # 지수 증가
  exponential-backoff-multiplier: 2 # 1s → 2s → 4s
  enable-randomized-wait: true      # Jitter 활성화
  randomized-wait-factor: 0.5       # ±50% 범위
```

실제 재시도 간격 예시:
- 1차 재시도: 1s × (1 ± 0.5) = 0.5~1.5초
- 2차 재시도: 2s × (1 ± 0.5) = 1~3초

## Retry Storm 문제

```
PG가 503 에러를 뱉는 상황:
- 클라이언트 100대 × 3번 재시도 = 300건 요청
- PG 입장에서는 원래 100건 → 300건으로 트래픽 3배
- 이미 과부하인 PG가 더 죽어감 → 재시도 안티패턴

해결: CircuitBreaker와 조합
- 실패율 50% 초과 → 서킷 Open → 재시도 자체 차단
- PG에 추가 부하를 주지 않으면서 회복 시간 확보
```

## 재시도 가능 예외 vs 불가능 예외

| 재시도 O (일시적 장애) | 재시도 X (영구적 장애) |
|---|---|
| SocketTimeoutException (Read Timeout) | HTTP 400 (잘못된 요청) |
| ConnectException (Connection Timeout) | HTTP 401 (인증 실패) |
| HTTP 500/503 (서버 에러) | 비즈니스 에러 (잔액 부족) |

**주의**: Read Timeout 재시도 시 PG가 이미 처리했을 수 있음 → **멱등성 보장 필수**

### (리뷰 대응) 왜 결제 POST에 @Retry를 걸면 안 되는가

초기 구현에서는 `requestPayment()`에 `@Retry`를 적용했으나, 코드리뷰에서 **치명적 결함**으로 지적됨:

```
시나리오: Read Timeout 후 자동 재시도

1차: Client → PG 요청 → PG 내부 접수 성공 → 응답 전송 중 타임아웃
     Client: SocketTimeoutException → @Retry가 2차 시도 트리거
2차: Client → PG 요청 → PG가 새 transactionKey로 별도 결제 생성 → 성공

결과: 같은 주문에 PG 결제 2건 → 이중 결제 사고
```

**근본 원인**: PG 시뮬레이터가 orderId 멱등성을 보장하지 않음 (같은 orderId로 매번 새 트랜잭션 생성).

**수정**: `requestPayment()`에서 `@Retry` 제거. `@CircuitBreaker`만 유지.
- `getTransactionStatus()`, `getTransactionsByOrderId()`: GET 조회는 멱등하므로 `@Retry` 적용

### (리뷰 대응) Retryable 예외 분류 좁히기

초기 구현에서는 `PaymentGatewayException` 전체를 재시도 대상으로 두었으나, 영구 실패(4xx 등)까지 재시도하는 문제가 있었음.

**수정**: `PaymentGatewayRetryableException`을 도입하여 재시도 가능한 예외만 분류:
- `ResourceAccessException` (타임아웃/연결 실패) → `PaymentGatewayRetryableException`
- `HttpServerErrorException` (5xx) → `PaymentGatewayRetryableException`
- 기타 (4xx, 비즈니스 에러) → `PaymentGatewayException` (재시도 안 함)

### (리뷰 대응) PENDING Payment orderId 기반 복구

초기 구현에서는 PENDING(transactionKey=null) Payment를 sync할 수 없었음. PG가 실제로는 접수했는데 우리만 key를 유실한 경우 복구 불가.

**수정**: `POST /payments/orders/{orderId}/sync` 엔드포인트 추가.
- PG의 `GET /payments?orderId=` API로 orderId 기반 조회
- 내부 PENDING 0건/2건+ → 자동 처리 거부 (정합성 사고)
- PG 결과 0건/2건+ → 자동 처리 거부 (미접수/이중 결제)
- 양쪽 모두 1건일 때만 정상 복구

## 실측 결과

### PG 단일 요청 성공률 (재시도 없음, 50회)
- 성공: 25/50 (50%)
- 실패: 25/50 (50%)
- (설계상 60% 성공률이지만 샘플 분산으로 50%)

### 재시도 3회 적용 시 이론적 성공률

| 단일 실패율 | 3회 재시도 후 실패 확률 | 최종 성공률 |
|---|---|---|
| 40% (설계값) | 0.4³ = 6.4% | **93.6%** |
| 50% (이번 측정) | 0.5³ = 12.5% | **87.5%** |

### 재시도 비용

| 항목 | 재시도 없음 | 재시도 3회 |
|---|---|---|
| 성공 시 PG 호출 수 | 1회 | 1회 (변함없음) |
| 실패 시 PG 호출 수 | 1회 | 최대 3회 |
| 실패 시 최대 대기 시간 | 2초 (Read Timeout) | 2초 + 1초 + 2초 + 2초 = 7초 |
| 전체 요청 대비 PG 부하 증가 | 1x | 최대 1.8x (40% 실패 시) |

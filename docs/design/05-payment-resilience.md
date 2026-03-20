# PG 비동기 결제 연동 — Resilience 설계 문서

## 1. 개요

PG 시뮬레이터와의 비동기 결제 연동에서 발생할 수 있는 모든 장애 지점을 식별하고,
Timeout → Retry → CircuitBreaker → Fallback 흐름으로 단계별 회복 전략을 설계한다.

---

## 2. PG 시뮬레이터 분석

### 2.1 API 스펙

| METHOD | URI | 설명 |
|--------|-----|------|
| POST | `/api/v1/payments` | 결제 요청 (비동기) |
| GET | `/api/v1/payments/{transactionKey}` | 결제 상태 확인 |
| GET | `/api/v1/payments?orderId={orderId}` | 주문별 결제 조회 |

### 2.2 결제 요청 Body

```json
{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/api/v1/payments/callback"
}
```

### 2.3 비동기 결제 흐름

```
[Commerce API]                    [PG Simulator]
     |                                  |
     |--- POST /api/v1/payments ------->|
     |                                  |-- (100~500ms 랜덤 지연)
     |                                  |-- (40% 확률: 즉시 500 에러)
     |                                  |-- (60% 확률: Payment 저장, status=PENDING)
     |<-- 200 {status: PENDING} --------|
     |                                  |
     |                                  |== [비동기 처리: 1~5초 후] ==
     |                                  |-- 70%: SUCCESS
     |                                  |-- 20%: FAILED (한도 초과)
     |                                  |-- 10%: FAILED (잘못된 카드)
     |                                  |
     |<-- POST callback (결과 통보) ----|
     |                                  |
```

### 2.4 PG 시뮬레이터 특성 정리

| 구간 | 특성 | 수치 |
|------|------|------|
| 요청 지연 | 랜덤 Thread.sleep | 100~500ms |
| 요청 실패 | 랜덤 500 에러 (서버 불안정 시뮬레이션) | **40% 확률** |
| 처리 지연 | 비동기 처리 대기 시간 | 1~5초 |
| 처리 성공 | 정상 승인 | 70% |
| 처리 실패 | 한도 초과 | 20% |
| 처리 실패 | 잘못된 카드 | 10% |
| 콜백 실패 | 콜백 POST 실패 시 로그만 남기고 **재시도 없음** | - |

### 2.5 PG 결제 상태

```
PENDING ──approve()──→ SUCCESS  (reason: "정상 승인되었습니다.")
PENDING ──limitExceeded()──→ FAILED  (reason: "한도초과입니다. 다른 카드를 선택해주세요.")
PENDING ──invalidCard()──→ FAILED  (reason: "잘못된 카드입니다. 다른 카드를 선택해주세요.")
```

- 상태 전이는 **PENDING에서만 가능** (단방향)
- SUCCESS/FAILED에서 다른 상태로 전이 불가

---

## 3. 장애 발생 지점 식별

결제 요청부터 결과 반영까지, 장애가 발생할 수 있는 **모든 지점**을 식별한다.

### 3.1 장애 지점 맵

```
[사용자 결제 요청]
       |
       ▼
┌─ F1. 내부 주문 검증 실패 (주문 없음, 이미 결제됨 등)
       |
       ▼
┌─ F2. PG 요청 전송 실패 (네트워크 연결 불가)
│  F3. PG 요청 타임아웃 (응답 지연 > 타임아웃)
│  F4. PG 500 에러 (40% 확률 서버 불안정)
       |
       ▼  (PG 응답: PENDING)
┌─ F5. PG 응답 수신했으나 내부 저장 실패
       |
       ▼  (비동기 대기)
┌─ F6. PG 비동기 처리 결과: FAILED (한도 초과/잘못된 카드)
│  F7. 콜백 미수신 (PG에서 콜백 전송 실패)
│  F8. 콜백 수신했으나 내부 처리 실패
       |
       ▼
┌─ F9. 타임아웃으로 실패 처리했으나, PG에서는 결제 성공 (유령 결제)
```

### 3.2 장애 지점별 분석

| ID | 장애 지점 | 발생 원인 | 심각도 | 발생 빈도 |
|----|----------|----------|--------|----------|
| **F1** | 내부 주문 검증 실패 | 존재하지 않는 주문, 이미 결제된 주문 | 낮음 | 드묾 |
| **F2** | PG 연결 실패 | 네트워크 단절, PG 서버 다운 | 높음 | 드묾 |
| **F3** | PG 응답 타임아웃 | PG 지연 (100~500ms 범위 초과) | 중간 | 보통 |
| **F4** | PG 500 에러 | 서버 불안정 시뮬레이션 | 중간 | **높음 (40%)** |
| **F5** | 내부 저장 실패 | DB 장애 등 | 높음 | 드묾 |
| **F6** | PG 비동기 처리 실패 | 한도 초과(20%), 잘못된 카드(10%) | 낮음 | 보통 (30%) |
| **F7** | 콜백 미수신 | PG 콜백 전송 실패 (재시도 없음) | **높음** | 보통 |
| **F8** | 콜백 수신 후 내부 처리 실패 | 서버 장애, DB 장애 | 높음 | 드묾 |
| **F9** | 유령 결제 | 타임아웃 실패 처리했으나 PG에서 결제 진행 | **매우 높음** | 보통 |

---

## 4. 내부 결제 상태 설계

### 4.1 왜 내부 결제 상태가 필요한가?

PG의 상태(PENDING/SUCCESS/FAILED)만으로는 우리 시스템의 모든 상태를 표현할 수 없다.

- PG 요청 자체가 실패한 경우 (PG에는 기록 없음)
- 타임아웃으로 결과를 모르는 경우 (PG에서는 처리 중일 수 있음)
- 콜백을 못 받은 경우 (PG에서는 이미 SUCCESS인데 우리는 모름)

### 4.2 내부 결제 상태 전이

```
REQUESTED ──PG 응답 PENDING──→ PENDING ──콜백 SUCCESS──→ PAID
    │                            │
    │                            ├──콜백 FAILED──→ FAILED
    │                            │
    │                            └──콜백 미수신 (일정 시간 초과)──→ UNKNOWN
    │
    ├──PG 요청 실패 (500/타임아웃)──→ FAILED
    │
    └──PG 요청 타임아웃 (PG에서 처리 가능성 있음)──→ UNKNOWN
```

| 내부 상태 | 의미 | PG 상태와의 관계 |
|----------|------|----------------|
| **REQUESTED** | 결제 요청 생성, PG 호출 전/중 | PG에 아직 기록 없을 수 있음 |
| **PENDING** | PG 응답 수신 (transactionKey 확보) | PG: PENDING |
| **PAID** | 결제 성공 확인 | PG: SUCCESS |
| **FAILED** | 결제 실패 확정 | PG: FAILED 또는 PG 요청 자체 실패 |
| **UNKNOWN** | 결과 불명 (타임아웃, 콜백 미수신) | PG 상태 확인 필요 |

### 4.3 왜 UNKNOWN 상태가 필요한가?

**근거**: 분산 시스템에서 "요청은 실패했지만, 상대방에서는 처리되었을 수 있는" 상황은 반드시 존재한다.

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. UNKNOWN 없이 FAILED로 처리 | 단순함 | **유령 결제 발생** — 고객 돈은 빠졌는데 주문은 실패 처리 |
| B. UNKNOWN 없이 PENDING 유지 | 상태가 적음 | PENDING이 "PG 처리 중"과 "결과 불명"을 혼재, 복구 로직 구분 불가 |
| **C. UNKNOWN 상태 분리** | **결과 불명을 명시적으로 표현, 복구 대상 식별 가능** | 상태 하나 추가 |

**결정: C. UNKNOWN 상태 분리**

- UNKNOWN 상태의 결제건만 골라서 PG 상태 확인 API로 복구할 수 있다
- 운영자가 "결과를 모르는 결제"를 즉시 식별할 수 있다
- 상태 하나 추가하는 비용 대비 안전성 확보 효과가 압도적이다

---

## 5. Timeout 설계

### 5.1 왜 Timeout이 필요한가?

PG 시뮬레이터는 요청 시 100~500ms 지연이 발생한다. 타임아웃이 없으면:
- PG가 느려질 때 스레드가 무한 대기 → 스레드 풀 고갈 → 전체 서비스 마비
- 주문, 상품 조회 등 결제와 무관한 기능까지 영향

### 5.2 타임아웃 값 결정

| 선택지 | 값 | 장점 | 단점 |
|--------|-----|------|------|
| A. 500ms | PG 최대 지연과 동일 | 빠른 실패 | 정상 요청도 잘릴 수 있음 |
| **B. 1,000ms (1초)** | **PG 최대 지연의 2배** | **정상 요청 대부분 수용, 비정상은 빠르게 차단** | - |
| C. 3,000ms (3초) | 넉넉한 여유 | 안전함 | 장애 시 스레드 3초간 점유, 빠른 실패 효과 감소 |

**결정: connectTimeout 500ms + readTimeout 1초**

**근거**:
- **connectTimeout 500ms**: TCP 연결 수립만 담당. PG가 살아있으면 수십ms 내 연결 완료. 500ms 내 연결 안 되면 PG 서버 자체가 불능
- **readTimeout 1초**: 연결 후 응답 대기. PG 정상 응답 100~500ms 기준, 2배 여유
- connectTimeout < readTimeout 분리 → PG 서버 다운 시 500ms 만에 빠른 실패, Retry로 즉시 전환
- 스레드 점유 시간을 최소화하여 다른 요청에 영향을 주지 않음

### 5.3 적용 위치

```java
// Feign Client 설정
@Bean
public Request.Options feignOptions() {
    return new Request.Options(
        500, TimeUnit.MILLISECONDS,   // connectTimeout — TCP 연결 수립
        1000, TimeUnit.MILLISECONDS,  // readTimeout — 응답 대기
        true                          // followRedirects
    );
}
```

---

## 6. Retry 설계

### 6.1 왜 Retry가 필요한가?

PG 시뮬레이터는 **40% 확률로 500 에러**를 반환한다.
이는 일시적 장애(transient fault)이며, 즉시 재시도하면 성공할 수 있다.

- 재시도 없이 1회 시도: 성공률 60%
- 2회 시도: 성공률 60% + (40% × 60%) = **84%**
- 3회 시도: 성공률 84% + (16% × 60%) = **93.6%**

### 6.2 재시도 정책 결정

| 항목 | 선택지 | 결정 | 근거 |
|------|--------|------|------|
| 최대 시도 횟수 | 2회 / **3회** / 5회 | **3회** | 93.6% 성공률 확보. 5회는 총 대기 시간 증가 대비 한계 효용 낮음 (98.4% → +4.8%) |
| 대기 전략 | 고정 / **지수 백오프** / 랜덤 | **지수 백오프** | 고정 간격은 PG 부하 회복 시간을 주지 않음. 지수 백오프로 간격을 점진적으로 늘려 PG 회복 여유 제공 |
| 초기 대기 시간 | 100ms / **500ms** / 1초 | **500ms** | PG 지연이 100~500ms이므로, 최소 500ms 후 재시도해야 PG 부하가 해소될 가능성 높음 |
| 재시도 대상 예외 | 모든 예외 / **특정 예외만** | **특정 예외만** | 400 에러(잘못된 요청)는 재시도해도 의미 없음. 500/타임아웃만 재시도 |

### 6.3 재시도 대상 vs 비대상

| 예외 유형 | 재시도 여부 | 근거 |
|----------|-----------|------|
| PG 500 에러 (서버 불안정) | **O** | 일시적 장애, 재시도 시 성공 가능 |
| 타임아웃 (SocketTimeoutException) | **O** | 네트워크 일시 지연, 재시도 시 성공 가능 |
| 연결 실패 (ConnectException) | **O** | 일시적 네트워크 불안정 가능 |
| PG 400 에러 (잘못된 요청) | **X** | 요청 데이터 문제, 재시도해도 동일 실패 |
| PG 404 에러 | **X** | 리소스 문제, 재시도 무의미 |

### 6.4 재시도와 멱등성

**핵심 문제**: 타임아웃으로 재시도했는데, 첫 번째 요청이 PG에서 이미 처리되었다면?

PG 시뮬레이터의 유니크 제약: `(user_id, order_id, transaction_key)` — 같은 orderId로 여러 번 요청하면 **별도 결제건이 생성된다**. 즉 PG 자체는 멱등하지 않다.

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. 재시도 시 동일 요청 그대로 전송 | 단순함 | **중복 결제 위험** — 같은 주문에 대해 PG 결제가 2건 발생 |
| **B. 내부에서 멱등성 보장** | **중복 결제 방지** | 약간의 구현 복잡도 |

**결정: B. 내부 멱등성 보장**

**방법**:
- 결제 요청 시 내부에 Payment 레코드를 먼저 생성 (status: REQUESTED)
- 재시도 전에 해당 주문의 Payment 상태 확인
- 이미 PENDING/PAID 상태이면 재시도하지 않고 기존 결제건 상태 확인으로 전환
- FAILED/UNKNOWN 상태이면 PG 상태 확인 API 호출 후 판단

### 6.5 Resilience4j 설정

```yaml
resilience4j:
  retry:
    instances:
      pgRetry:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2  # 500ms → 1s → 2s
        retry-exceptions:
          - feign.RetryableException
          - java.net.SocketTimeoutException
          - java.net.ConnectException
        ignore-exceptions:
          - feign.FeignException.BadRequest
          - feign.FeignException.NotFound
        fail-after-max-attempts: true
```

### 6.6 최악 시나리오 시간 계산

```
1차 시도: 1초 (타임아웃) + 500ms (대기) = 1.5초
2차 시도: 1초 (타임아웃) + 1초 (대기) = 2초
3차 시도: 1초 (타임아웃)
총 최대: 4.5초
```

사용자 입장에서 4.5초는 결제 UX로 허용 가능한 범위이다. (일반적으로 결제는 5~10초 대기 용인)

---

## 7. Circuit Breaker 설계

### 7.1 왜 Circuit Breaker가 필요한가?

Retry만으로는 PG **전면 장애** 상황에 대응할 수 없다.

- PG가 완전히 다운되면 모든 요청이 3회씩 재시도 → 실패
- 초당 100건 결제 요청 시: 100 × 3 = 300건이 PG에 몰림
- 불필요한 재시도로 PG 회복을 더 늦추고, 내부 스레드도 고갈

Circuit Breaker는 **"더 이상 보내지 말자"**를 결정하는 장치이다.

### 7.2 설정값 결정

| 항목 | 선택지 | 결정 | 근거 |
|------|--------|------|------|
| 슬라이딩 윈도우 크기 | 5 / **10** / 20 | **10** | 너무 작으면 일시적 실패에 과민 반응, 너무 크면 장애 감지 느림 |
| 실패율 임계치 | 30% / **50%** / 70% | **50%** | PG 정상 실패율(40%)보다 높게 설정. 정상 운영 중 회로가 열리지 않도록 |
| Open 상태 유지 시간 | 5s / **10s** / 30s | **10s** | PG 복구에 충분한 시간 부여. 너무 길면 복구 후에도 차단 지속 |
| Half-Open 허용 호출 수 | 1 / **2** / 5 | **2** | 1건은 네트워크 불안정에 의한 오판 위험. 2건이면 신뢰도 확보 |
| 느린 호출 기준 | 1s / **2s** / 3s | **2s** | PG 정상 응답 500ms 기준, 4배 이상 느리면 비정상 |
| 느린 호출 비율 임계치 | 30% / **50%** / 70% | **50%** | 절반 이상 느리면 PG 과부하 |

### 7.3 실패율 임계치를 50%로 설정한 이유 (상세)

PG 시뮬레이터의 정상 요청 실패율은 40%이다.

```
임계치 40% → 정상 운영 중에도 회로가 열릴 수 있음 (위험)
임계치 50% → 정상 운영에서는 열리지 않고, 추가 장애 시에만 Open
임계치 70% → 장애 감지가 너무 느림
```

50%는 PG의 기본 실패율(40%)에 10%p 여유를 둔 값이다.
10건 중 5건 이상 실패하면 PG에 추가적인 문제가 있다고 판단할 수 있다.

### 7.4 CB 세분화: PG × API 유형별 분리

**원칙**: 결제 요청 CB가 Open되어도 상태 조회 CB는 Closed → 복구 로직 계속 동작.

```
[치명적 시나리오 — CB를 분리하지 않으면]
PG 결제 요청 대량 실패 → CB Open → 상태 조회도 차단
→ Outbox, 배치, Polling 전부 PG 조회 불가 → 복구 경로 전체 마비
```

#### CB 인스턴스 목록 (3개 — 쓰기만)

> 읽기 CB 제거 근거: 06 §18 참조.
> 상태 확인은 "복구 행위"이므로 CB가 차단하면 복구가 멈춘다.
> 읽기는 Timeout + Rate Limiter + 드라이버 내장 폴백으로 충분하다.

| CB 인스턴스 | 대상 | 성격 |
|------------|------|------|
| `pgSimulator-request` | Simulator 결제 요청 (POST) | 쓰기 |
| `pgToss-request` | Toss 결제 승인 (POST) | 쓰기 |
| `redis-write` | Redis 가주문 생성 + 재고 DECR (Master) | 쓰기 |

#### CB별 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      # --- PG 결제 요청 (쓰기만 CB 적용) ---
      pgSimulator-request:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
        record-exceptions:
          - feign.RetryableException
          - java.net.SocketTimeoutException
          - java.net.ConnectException
        ignore-exceptions:
          - feign.FeignException.BadRequest
          - feign.FeignException.NotFound

      pgToss-request:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s       # Toss는 안정적, 복구 여유 더 줌
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 3s

      # --- Redis 쓰기 (Master — 가주문 생성, 재고 DECR) ---
      redis-write:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s    # Redis는 복구가 빠르므로 짧게
        permitted-number-of-calls-in-half-open-state: 3
        record-exceptions:
          - org.springframework.data.redis.RedisConnectionFailureException
          - io.lettuce.core.RedisCommandTimeoutException
          - org.springframework.data.redis.RedisSystemException

      # --- 읽기 CB 없음 (06 §18 근거) ---
      # PG 상태 조회: 복구 행위이므로 CB 차단 시 복구 지연 → Timeout만으로 보호
      # Redis 읽기: Lettuce ReadFrom.REPLICA_PREFERRED 내장 폴백 → commandTimeout + try-catch
```

#### Rate Limiter

##### 결제 요청: Sliding Window Counter (직접 구현)

```java
/**
 * PG 결제 요청 Rate Limiter — Sliding Window Counter 방식
 *
 * Fixed Window 경계 돌파(Boundary Burst) 문제 해결:
 * - Fixed Window: 윈도우 경계에서 최대 2배(100건) burst 가능
 * - Sliding Window Counter: 어떤 1초 구간에서도 정확히 50건 이하 보장
 *
 * 이전 윈도우의 잔여 비중 × 이전 카운트 + 현재 카운트 ≤ limit
 */
@Component
public class SlidingWindowRateLimiter {

    private final int limit;               // 50 (초당 최대)
    private final long windowSizeMs;       // 1000ms

    private final AtomicLong prevWindowStart = new AtomicLong(0);
    private final AtomicInteger prevWindowCount = new AtomicInteger(0);
    private final AtomicLong currWindowStart = new AtomicLong(0);
    private final AtomicInteger currWindowCount = new AtomicInteger(0);

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs * windowSizeMs;

        if (currentWindow != currWindowStart.get()) {
            prevWindowCount.set(currWindowCount.get());
            prevWindowStart.set(currWindowStart.get());
            currWindowCount.set(0);
            currWindowStart.set(currentWindow);
        }

        double prevWeight = Math.max(0, 1.0 - (double)(now - currentWindow) / windowSizeMs);
        double weightedCount = prevWeight * prevWindowCount.get() + currWindowCount.get();

        if (weightedCount < limit) {
            currWindowCount.incrementAndGet();
            return true;
        }
        return false;  // → 429 Too Many Requests 응답
    }
}
```

- **적용 이유**: 결제 요청은 동시 트래픽이 발생하는 지점. PG 계약 TPS 50을 정확히 지켜야 함
- **Fixed Window 대비**: 경계 돌파 없이 어떤 1초 구간에서도 50건 이하 보장
- **"limit을 보수적으로 설정"하면?**: limit=25로 하면 정상 시 처리량 50% 낭비 → 비즈니스 손실

##### 배치 조회: Fixed Window (Resilience4j)

```yaml
resilience4j:
  ratelimiter:
    instances:
      pgStatusBatch:
        limit-for-period: 10          # 주기당 최대 10건
        limit-refresh-period: 1s      # 1초 주기
        timeout-duration: 0           # 초과 시 즉시 실패 (대기 안 함)
```

- **Fixed Window 유지 이유**: 배치 스케줄러가 1건씩 순차 호출 → 동시성 없음 → 경계 돌파 구조적 불가능

#### 장애 격리 검증

| 장애 시나리오 | 방어 수단 | 차단되는 기능 | 정상 동작하는 기능 |
|-------------|----------|------------|----------------|
| Simulator 결제 처리 장애 | CB `pgSimulator-request` Open | Simulator 결제 | **Toss 결제, 모든 상태 조회, 모든 복구** |
| 플래시 세일 동시 결제 폭증 | SlidingWindowRateLimiter (50 req/sec) | 초과 요청 429 응답 | **PG 과부하 방지, CB Open 예방** |
| 배치가 Simulator 과부하 | Rate Limiter `pgStatusBatch` | - | **전부 정상** |
| 모든 PG 결제 장애 | CB `*-request` 전부 Open | 모든 결제 | **모든 상태 조회 → 복구 동작** |
| Redis Master 장애 | CB `redis-write` Open | 가주문 쓰기 | **DB 직접 주문 Fallback, Replica 읽기 정상** |
| Redis 전체 장애 | CB `redis-write` Open + 읽기 try-catch | 가주문 쓰기 | **DB 직접 주문 + DB 조회 Fallback (Timeout 500ms)** |
| Redis-DB 재고 불일치 | 정합성 배치 — Lua Script v2 (30초 주기) | - | **DB 기준 Redis 원자적 보정** |

### 7.5 SlidingWindowRateLimiter → Retry → Circuit Breaker 실행 순서

```
결제 요청:
  [SlidingWindowRateLimiter]          ← 최근 1초간 50건 초과? → 429 응답
   │ (통과)
   ▼
  [@Retry: pgSimulatorRetry]          ← 실패 시 1회 재시도 (500ms 대기)
   │
   ▼
  [@CircuitBreaker: pgSimulator-request] ← 실패율 50% 초과? → Fallback (다른 PG)
   │
   ▼
  [Feign Client] → PG 호출

상태 조회 (실시간) — CB 없음:
  [Feign Client] → PG 조회 (Timeout 1초, try-catch → UNKNOWN 반환)
  (복구 행위이므로 CB로 차단하지 않음 → 06 §18 근거)

상태 조회 (배치) — CB 없음, Rate Limiter만:
  [@RateLimiter: pgStatusBatch] → PG 조회 (Timeout 1초)
```

```
실행 순서 근거:
- Sliding Window Rate Limiter가 가장 바깥:
  PG 계약 TPS를 정확히 지킴 (경계 돌파 없음)
- Rate Limiter 거부는 CB에 기록하지 않음:
  트래픽 초과 ≠ PG 장애. CB에 기록하면 트래픽만 많아도 CB Open → 오작동
- Retry가 CB 바깥: 재시도 실패도 CB에 기록되어야 정확한 실패율 측정
- CB가 가장 안쪽: 최종 차단 판단 + Fallback 트리거
```

```java
// 결제 요청 — Sliding Window Rate Limiter (Interceptor) + Retry + request CB
// SlidingWindowRateLimiter는 PaymentRateLimiterInterceptor에서 적용
@Retry(name = "pgSimulatorRetry")
@CircuitBreaker(name = "pgSimulator-request", fallbackMethod = "requestFallback")
public PgPaymentResponse requestPayment(PgPaymentRequest request) { ... }

// 상태 조회 (실시간) — CB 없음, Timeout + try-catch만으로 보호
public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
    try {
        return pgClient.getPaymentStatus(transactionKey);  // Feign timeout 1초
    } catch (Exception e) {
        log.warn("PG 상태 확인 실패: transactionKey={}", transactionKey, e);
        return PgPaymentStatusResponse.unknown(transactionKey);
    }
}

// 상태 조회 (배치) — Rate Limiter만, CB 없음
@RateLimiter(name = "pgStatusBatch")
public PgPaymentStatusResponse getPaymentStatusForBatch(String transactionKey) {
    try {
        return pgClient.getPaymentStatus(transactionKey);
    } catch (Exception e) {
        log.warn("PG 상태 확인 실패 (배치): transactionKey={}", transactionKey, e);
        return PgPaymentStatusResponse.unknown(transactionKey);
    }
}
```

### 7.6 Half-Open 전략

#### 문제: 고정 대기 + 고객 트래픽으로 테스트

```
CB Open → 10초 고정 대기 → Half-Open → 실제 결제 요청 2건으로 테스트
                                        ↑ 고객이 실험 대상 (돈이 걸림)
```

- PG 2초에 복구 → 8초 낭비 (쿠팡 기준 수천 건 결제 손실)
- PG 30초 복구 → 10초마다 실패 반복 → 복구 방해
- 테스트 2건 성공 → 즉시 Closed → 전량 트래픽 폭주 (Thundering Herd)

#### 개선 1: Progressive Backoff (Open 반복 시 대기 시간 증가)

```
1차 Open: 5초 → Half-Open
  실패 → 2차 Open: 10초 → Half-Open
  실패 → 3차 Open: 20초 → Half-Open
  실패 → 4차 Open: 40초 → Half-Open
  실패 → 5차+ Open: 60초 cap
  성공 → Closed (카운트 리셋)
```

- 단순 일시 장애: 5초 만에 복구 → 최소 다운타임
- PG 재시작(30초~1분): 10~20초 시점에 복구 감지
- 전면 장애(수 분): 60초 간격 체크 → PG 부하 최소화

#### 개선 2: Health Check Probe (결제 요청 CB 전용)

**결제 요청은 돈이 걸린 작업. 실제 고객 요청을 테스트로 쓰면 안 된다.**

```
CB Open 중:
  Health Probe 스케줄러 → GET /payments?orderId=HEALTH_CHECK
  → 200 or 404 (서버 응답) → CB.transitionToHalfOpenState()
  → 500 or 타임아웃 (장애) → 대기 계속
```

```java
@Component
public class PgHealthChecker {
    public boolean isSimulatorHealthy() {
        try {
            simulatorClient.getPaymentByOrderId("HEALTH_CHECK");
            return true;   // 200 — 서버 정상
        } catch (FeignException.NotFound e) {
            return true;   // 404 — 서버 살아있음, 데이터만 없음
        } catch (Exception e) {
            return false;  // 타임아웃/500 — 장애
        }
    }
}
```

> 200이든 404든 "응답이 왔다" = PG가 살아있다는 증거.

#### CB 유형별 Half-Open 전략

> 읽기 CB 제거(06 §18)로 Half-Open 전략은 **쓰기 CB + redis-write**에만 적용.

| CB 유형 | 전환 방식 | 테스트 방법 | 근거 |
|---------|----------|-----------|------|
| `*-request` | **Health Probe** → 수동 전환 | Probe 성공 → Half-Open → 실제 2건 | 결제는 돈, 고객을 실험 대상으로 쓰지 않음 |
| `redis-write` | **Progressive Backoff** | Redis PING 확인 → Half-Open → 실제 3건 | Redis 복구가 빠르므로 짧은 backoff |

#### 결제 요청 CB Half-Open 전체 흐름

```
pgSimulator-request CB Open
  │
  ├── [Health Probe: 5초 후] GET /payments?orderId=HEALTH_CHECK
  │     ├── OK → CB.transitionToHalfOpenState()
  │     │     → 실제 결제 2건 허용 → 성공 → Closed (카운트 리셋)
  │     │                         → 실패 → Open (카운트 +1, 다음 10초)
  │     └── 실패 → 대기
  │
  ├── [Health Probe: 10초 후] 재시도 (카운트 1)
  ├── [Health Probe: 20초 후] 재시도 (카운트 2)
  └── [Health Probe: 60초 cap] 재시도 (카운트 4+)
```

---

## 8. Fallback 설계

### 8.1 Fallback 설계 원칙

Fallback은 "에러를 잡아서 안전한 응답을 주는 것"이 아니라,
**장애가 발생해도 비즈니스가 계속 동작하는 대체 경로를 확보**하는 것이다.

### 8.2 7계층 Fallback 체계

```
[1차 방어] Timeout → 개별 요청 시간 제한
[2차 방어] Retry → 일시적 실패 재시도 (멱등성 보장)
[3차 방어] Circuit Breaker → 반복 실패 시 호출 차단
[4차 방어] Multi-PG Fallback → 대체 PG로 자동 전환
[5차 방어] Polling Hybrid → 콜백 실패 시 능동적 확인
[6차 방어] Callback DLQ → 콜백 데이터 보존 + 재처리
[7차 방어] Local WAL → DB 장애 시 PG 응답 보존
[최종 방어] UNKNOWN + Outbox + 배치 → 모든 방어가 뚫려도 최종 복구
```

### 8.3 FB-PG: Multi-PG Fallback (PG 인프라 장애)

#### 아키텍처

```
[결제 요청]
     │
     ▼
[PG Router (Strategy)]
     ├── 1순위: PG Simulator (Primary)
     │     └── CB → Retry → 성공 시 리턴
     │
     ├── Primary 실패 (CB Open 또는 Retry 소진)
     │     ▼
     ├── 2순위: Toss Payments Sandbox (Fallback)
     │     └── CB → Retry → 성공 시 리턴
     │
     └── 모든 PG 실패
           ▼
     [최종 Fallback: UNKNOWN 저장 + "확인 중" 응답]
```

#### PG 추상화 (Strategy Pattern)

```java
public interface PgClient {
    PgPaymentResponse requestPayment(PgPaymentRequest request);
    PgPaymentStatusResponse getPaymentStatus(String transactionKey);
    PgPaymentStatusResponse getPaymentByOrderId(String orderId);
    String getProviderName();  // "SIMULATOR" / "TOSS"
}
```

```java
@Component
public class PgRouter {
    private final List<PgClient> pgClients;  // 우선순위 순

    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        for (PgClient client : pgClients) {
            try {
                return client.requestPayment(request);
            } catch (Exception e) {
                log.warn("PG [{}] 실패, 다음 PG 시도", client.getProviderName(), e);
            }
        }
        throw new AllPgFailedException();
    }
}
```

#### Fallback PG 전환 판단 기준

| 실패 유형 | PG 도달 가능성 | Fallback 전환 | 이유 |
|----------|-------------|-------------|------|
| ConnectException | 없음 | **전환** | PG에 요청 자체가 안 감 |
| 500 에러 | 낮음 | **전환** | PG가 요청을 처리하지 못함 |
| SocketTimeoutException | **있음** | **전환하지 않음** | PG에서 처리 중일 수 있음 → 중복 결제 위험 |
| CB Open | - | **전환** | PG 전면 장애 판단 |
| 400 에러 | - | **전환하지 않음** | 요청 자체가 잘못됨 |

#### PG별 차이 추상화

| 항목 | PG Simulator | Toss Sandbox |
|------|-------------|-------------|
| 결제 방식 | 비동기 (콜백) | 동기 (즉시) |
| 멱등성 | 미지원 (수동 보장) | Idempotency-Key 지원 |
| 응답 | PENDING → 콜백 | SUCCESS/FAILED 즉시 |

```
PgClient.requestPayment() 반환값에 따라 PaymentFacade 분기:
  - PENDING → Payment(PENDING) + 콜백 대기
  - SUCCESS → Payment(PAID) + 주문 확정 (즉시)
  - FAILED  → Payment(FAILED) + 재고 복원 (즉시)
```

#### PG별 독립 CB/Retry 설정

> CB 세분화 상세는 Section 7.4 참조.
> PG별 request CB 2개 + Redis write CB 1개 = **총 3개** (읽기 CB 제거, 06 §18 근거).

```yaml
resilience4j:
  retry:
    instances:
      pgSimulatorRetry:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
      pgTossRetry:
        max-attempts: 2    # Toss는 안정적이므로 적게
        wait-duration: 500ms
```

### 8.4 FB-POLL: Polling Hybrid (콜백 채널 장애)

콜백만 기다리면 유실 시 최대 1분(배치 주기)간 상태 미확정.
**능동적 폴링을 추가하여 10초 내 복구.**

```
PG 응답(PENDING) 수신 시:
  → [정상 경로] 콜백 대기
  → [대체 경로] Delayed Task 등록 (T+10초 후 실행)

10초 내 콜백 수신 → Task 취소
10초 후 콜백 미수신 → Task 실행:
  1. GET /api/v1/payments/{transactionKey}
  2. PG 상태에 따라 내부 상태 전이
  3. 아직 PENDING이면 → 20초 후 재확인 Task 등록
```

```java
// PG 응답(PENDING) 수신 직후
taskScheduler.schedule(
    () -> paymentRecoveryService.checkAndRecover(paymentId),
    Instant.now().plusSeconds(10)
);
```

### 8.5 FB-DLQ: Callback Inbox (콜백 처리 장애)

PG 콜백 수신 후 내부 처리 중 예외 → 콜백 데이터 유실 방지.
**PG에게 항상 200 OK를 먼저 반환하고, 원본을 보존한 채 비동기 처리.**

```
콜백 수신 → [1단계] callback_inbox 테이블에 원본 저장 (RECEIVED)
          → [2단계] 비즈니스 처리
          → [성공] PROCESSED
          → [실패] RECEIVED 상태 유지 → DLQ 스케줄러가 재처리
```

```sql
CREATE TABLE callback_inbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_key VARCHAR(50) NOT NULL,
    order_id        VARCHAR(50) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- 'RECEIVED' / 'PROCESSED' / 'FAILED'
    received_at     DATETIME NOT NULL,
    processed_at    DATETIME,
    retry_count     INT DEFAULT 0,
    error_message   VARCHAR(500)
);
```

### 8.6 FB-WAL: Local Write-Ahead Log (내부 DB 장애)

PG 결제 성공 → 내부 DB 저장 실패 → Payment 레코드 자체가 없으면 배치도 못 잡음.
**PG 응답을 DB와 독립적인 저장소에 먼저 기록.**

```
PG 응답 수신 즉시:
  1. [WAL] 로컬 파일에 {orderId, transactionKey, pgResponse} 기록
  2. [DB]  Payment 상태 업데이트 시도
     - 성공 → WAL 레코드 삭제
     - 실패 → WAL에 남아있음

[WAL Recovery 스케줄러]
  WAL에 남아있는 레코드 → DB에 반영 재시도 → 성공 시 WAL 삭제
```

### 8.7 최종 Fallback: UNKNOWN 상태

모든 방어 계층이 실패한 경우:

```java
public PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
    // 1. Payment 상태를 UNKNOWN으로 전이
    // 2. Outbox + 배치 복구 대상으로 등록
    // 3. 사용자 응답: "결제 확인 중입니다. 잠시 후 확인해주세요"
    // 4. 로그 남김 (운영 알림)
}
```

### 8.8 FB-ASYNC: 비동기 결제 고유 Fallback

비동기 결제는 "요청 성공 이후"에도 불확실 구간이 존재한다.
이 구간의 장애에 대한 Fallback을 별도로 설계한다.

#### A2. PG PENDING 최대 허용 시간

PG 비동기 처리 중 PG 크래시 → 영원히 PENDING → 고객 결제 영원히 미확정.

**정책: PENDING 5분 초과 시 FAILED 처리 + 재고 복원**

```
PG 처리 최대 5초 × 안전 마진 = 5분
→ 5분 넘게 PENDING이면 PG 측 장애로 판단
→ FAILED 처리 → 고객 재결제 가능
→ PG에서 뒤늦게 SUCCESS 콜백 → 조건부 UPDATE가 무시 (이미 FAILED)
→ 불일치 해소: PG 대사(reconciliation) 운영 프로세스
```

#### A3. PENDING 상태 콜백 무시

콜백 status가 PENDING이면 상태 전이하지 않고 무시. **SUCCESS/FAILED만 처리.**

#### A4. 콜백 채널 불안정 → 동기 PG 우선 전환

비동기 결제의 **가장 근본적인 Fallback**: 불확실 구간 자체를 제거.

```
[콜백 신뢰율 모니터링]
최근 N건 "PENDING 응답 → 10초 내 콜백 수신" 비율 추적
→ 50% 미만: 콜백 채널 불안정 판단
→ PgRouter가 Toss(동기) 우선 라우팅
→ 동기 PG = 요청 즉시 결과 확정 = 불확실 구간 없음
```

### 8.9 구현 범위

| # | Fallback 전략 | 현재 과제 | MSA/프로덕션 |
|---|-------------|----------|-------------|
| FB-PG | Multi-PG Routing | **구현** | N개 PG 확장 |
| FB-POLL | Polling Hybrid | **구현** | Delayed Queue (Kafka) |
| FB-DLQ | Callback Inbox | **구현** (DB 테이블) | Kafka DLQ |
| FB-WAL | Local WAL | **구현** (로컬 파일) | Redis/Kafka WAL |
| FB-COMP | 보상 트랜잭션 큐 | 불필요 (모노리스) | Saga Pattern |
| FB-CARD | 카드사별 모니터링 | 설계만 | CB per 카드사 |

---

## 9. 콜백 수신 및 상태 동기화

### 9.1 콜백 수신 API

```
POST /api/v1/payments/callback
Body: {
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1351039135",
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다.",
  ...
}
```

### 9.2 콜백 수신 시 처리 흐름

```
콜백 수신
   ├── [1단계] callback_inbox에 원본 저장 → PG에게 즉시 200 OK 반환
   │
   ├── [2단계] transactionKey로 내부 Payment 조회
   │      └── 없으면? → 로그 남기고 무시
   │
   ├── [3단계] 콜백 status 확인
   │      └── PENDING → 무시 (최종 결과가 아님, SUCCESS/FAILED만 처리)
   │
   ├── [4단계] 조건부 UPDATE로 상태 전이
   │      └── UPDATE payment SET status = ? WHERE id = ? AND status IN ('PENDING', 'UNKNOWN')
   │      └── affected rows = 0 → 이미 다른 경로(배치/폴링)에서 처리됨, 무시
   │
   ├── [5단계] 콜백 status에 따라
   │      ├── SUCCESS → PAID + 주문 상태 업데이트
   │      └── FAILED → FAILED + 주문/재고 롤백
   │
   └── [5단계] callback_inbox status → PROCESSED
```

### 9.3 콜백 멱등성 + 동시성 보호

**조건부 UPDATE**로 멱등성과 동시성을 동시에 보장한다.

```sql
UPDATE payment SET status = 'PAID'
WHERE id = ? AND status IN ('PENDING', 'UNKNOWN')
-- affected rows = 0이면 이미 확정됨 → 추가 처리 없이 종료
```

- 콜백과 배치/폴링이 동시에 같은 Payment를 처리해도, 먼저 UPDATE 성공한 쪽이 확정
- 나중에 UPDATE한 쪽은 affected rows = 0 → 충돌 없이 종료
- 락 없이 원자적 전이, 성능 영향 최소화

---

## 10. 상태 복구 (Recovery)

### 10.1 왜 복구 로직이 필요한가?

PG 시뮬레이터의 콜백은 **재시도하지 않는다.** 콜백 전송 실패 시 로그만 남긴다.
즉 다음 상황이 발생할 수 있다:
- 콜백 시점에 우리 서버가 다운 → 콜백 유실
- 네트워크 문제로 콜백 미도달
- 타임아웃으로 UNKNOWN 처리된 건이 PG에서는 SUCCESS

이런 결제건은 영원히 PENDING/UNKNOWN 상태에 머무르게 된다.

### 10.2 복구 전략 결정

| 선택지 | 동작 | 장점 | 단점 |
|--------|------|------|------|
| A. 수동 복구만 | 관리자가 직접 확인 | 단순 | 운영 부담, 누락 위험 |
| **B. 수동 API + 배치 폴링** | **확인 API 제공 + 주기적 배치로 미확인 건 조회** | **자동 복구 + 수동 보조** | 배치 모듈에 로직 추가 필요 |
| C. 이벤트 기반 | 상태 변경 이벤트 발행 | 느슨한 결합 | 현재 불필요한 복잡성 |

**결정: B. 수동 API + 배치 폴링**

### 10.3 수동 복구 API

```
POST /api/v1/payments/{paymentId}/confirm
```

- PENDING/UNKNOWN 상태인 결제건에 대해 PG 상태 확인 API 호출
- PG 응답에 따라 내부 상태 업데이트

### 10.4 배치 복구 (commerce-batch)

```
주기: 1분마다
대상:
  - status = REQUESTED 이면서 생성 후 1분 경과 (TX-1 후 PG 호출 전 크래시)
  - status = PENDING 이면서 생성 후 1분 경과 (콜백 미수신)
  - status = UNKNOWN (결과 불명)

동작:
  1. 대상 Payment 목록 조회
  2. 각 건에 대해:
     a. REQUESTED → PG 상태 확인 (GET /payments?orderId=xxx)
        - PG에 기록 있음 → transactionKey 저장 + 상태 동기화
        - PG 404 → FAILED 처리 (PG에 도달하지 못한 요청)
     b. PENDING/UNKNOWN → PG 상태 확인 (GET /payments/{transactionKey})
        - PG SUCCESS → 조건부 UPDATE로 PAID
        - PG FAILED → 조건부 UPDATE로 FAILED
        - PG PENDING + 생성 후 5분 미만 → 유지 (아직 처리 중)
        - PG PENDING + 생성 후 5분 초과 → FAILED 처리 + 재고 복원 (PG 측 장애 판단)
  3. 조건부 UPDATE 사용 (콜백/폴링과의 동시성 보호)
```

> **REQUESTED 포함 근거**: TX-1(Payment 저장) 커밋 후 PG 호출 전에 서버 크래시 시,
> Payment는 REQUESTED 상태로 영구 방치된다. Outbox 폴러가 1차 방어, 배치가 최종 안전망.

### 10.5 PENDING 타임아웃 기준

PG 비동기 처리는 최대 5초 소요. 안전 마진을 두고 **생성 후 1분 경과한 PENDING은 복구 대상**으로 판단한다.

**근거**:
- PG 처리 최대 5초 + 콜백 전송 시간 고려해도 30초면 충분
- 1분은 충분한 여유이며, 정상 흐름에서 배치가 불필요하게 개입하지 않음

### 10.6 대사 배치 (Reconciliation) — 교차 시스템 정합성 검증

> **복구 배치**는 "우리 시스템 내부의 비정상 상태를 고치는 것"이고,
> **대사 배치**는 "두 시스템의 기록을 대조하여 불일치를 감지하는 것"이다. (06 §22 근거)

```
복구와 대사의 관계:
  복구가 완벽하면 대사에서 불일치가 0건이어야 한다.
  → 대사는 "복구가 잘 동작하는지 검증하는 최종 안전망"
  → 대사에서 불일치가 발견되면 = 복구 로직에 버그가 있다는 신호
```

#### [R1] PG ↔ Payment 대사 배치

```
주기: 1시간
대상: 최근 24시간 내 Payment 중 status = PAID 또는 FAILED

동작:
  1. Payment에서 대상 조회 (reconciled = false)
  2. 각 건에 대해 PG 상태 확인 (GET /payments/{transactionKey})
  3. 대조:
     | 우리 상태 | PG 상태 | 판정 |
     |----------|---------|------|
     | PAID | SUCCESS | ✅ 일치 → reconciled = true |
     | PAID | FAILED | 🔴 불일치 → 알림 + 수동 확인 |
     | PAID | PENDING | 🟡 PG 미확정 → 다음 주기에 재확인 |
     | PAID | 404 | 🔴 PG에 기록 없음 → 알림 |
     | FAILED | SUCCESS | 🔴 환불 누락 → 알림 + 조건부 자동 보상 |
     | FAILED | FAILED | ✅ 일치 → reconciled = true |
  4. 불일치 → reconciliation_mismatch 테이블에 기록 + 운영 알림

PG 부하:
  현재 과제 규모 하루 ~1000건 가정
  Rate Limiter 10 req/sec → 1000건 / 10 = 100초 → 1시간 대비 부하율 3.3%
```

#### [R2] Payment ↔ Order 대사 배치

```
주기: 1시간
대상: 같은 DB (모놀리스) → JOIN 쿼리 1건

SELECT p.id, p.status as payment_status, o.status as order_status
FROM payment p
JOIN orders o ON p.order_id = o.id
WHERE (p.status = 'PAID' AND o.status != 'PAID')
   OR (p.status = 'FAILED' AND o.status NOT IN ('CANCELLED', 'CREATED'))

→ 결과 0건 = 정상, 1건 이상 = 운영 알림
모놀리스 이점: JOIN 1건으로 끝. MSA면 양쪽 API 호출 + 매칭 로직 필요.
```

#### [R3] Payment ↔ Coupon 대사 배치

```
주기: 1시간
대상: 같은 DB → JOIN 쿼리 1건

SELECT p.id, p.status, ci.id as coupon_issue_id, ci.status as coupon_status
FROM payment p
JOIN coupon_issue ci ON p.coupon_issue_id = ci.id
WHERE p.status IN ('FAILED', 'CANCELLED')
  AND ci.status = 'USED'

→ 결과 있으면: 쿠폰 복원 누락 → 자동 복원 (couponFacade.restoreCoupon)
→ 복원 후 로그 + 메트릭 기록
```

#### 복구 배치 vs 대사 배치 전체 구조

```
[실시간 복구 — 빠르게 고친다]
  Outbox Poller (5초)           → PG 호출 누락 재시도
  Callback DLQ                  → 콜백 처리 실패 재시도
  Polling Hybrid (10초)         → 콜백 미수신 시 능동 확인

[주기적 복구 — 놓친 건을 잡는다]
  Payment Recovery (1분)        → REQUESTED/PENDING/UNKNOWN 복구
  Stock Reconcile (30초)        → Redis-DB 재고 보정 (Lua Script)
  Proactive Expiry Scanner (30초) → 가주문 TTL 만료 선제 정리

[대사 — 전수 검증한다]
  PG ↔ Payment (1시간)          → PAID/FAILED 건 PG 대조 [R1]
  Payment ↔ Order (1시간)       → 상태 불일치 감지 [R2]
  Payment ↔ Coupon (1시간)      → 쿠폰 복원 누락 감지 + 자동 복원 [R3]
```

---

## 11. 전체 흐름 통합

### 11.1 정상 흐름

```
[사용자] POST /api/v1/payments
   → [PaymentFacade] 주문 검증
   → [TX-1] Payment(REQUESTED) + PaymentOutbox(PENDING) 저장
   → [PgRouter] Simulator CB → Retry → PG 요청
   → PG 응답 200: {status: PENDING, transactionKey: "xxx"}
   → [WAL] 로컬 기록
   → [TX-2] Payment(PENDING) + Outbox(PROCESSED) + Delayed Task 등록(10초)
   → 사용자 응답: "결제 처리 중"

... (1~5초 후) ...

[PG] POST /api/v1/payments/callback
   → [Callback Inbox] 원본 저장 → PG에게 200 OK
   → [조건부 UPDATE] Payment → PAID, Order → PAID
   → Delayed Task 취소
   → Callback Inbox → PROCESSED
```

### 11.2 Primary PG 실패 → Fallback PG 전환

```
[PgRouter] Simulator 1차 → 500 → 2차 → 500 → 3차 → 500
→ Simulator 실패, Toss Sandbox 시도
[PgRouter] Toss 1차 → SUCCESS (동기 즉시 응답)
→ [TX-2] Payment(PAID) + Order(PAID) (콜백 대기 불필요)
→ 사용자 응답: "결제 완료"
```

### 11.3 모든 PG 실패 → 최종 Fallback

```
[PgRouter] Simulator 3회 실패 → Toss 2회 실패
→ AllPgFailedException
→ [Fallback] Payment(UNKNOWN) + Outbox 유지
→ 사용자 응답: "결제 확인 중, 잠시 후 확인해주세요"
→ [Outbox 폴러 5초] → [배치 1분] → 복구
```

### 11.4 콜백 미수신 → Polling Hybrid 복구

```
[PG 응답 PENDING] → Delayed Task 등록 (10초)
... 10초 경과, 콜백 안 옴 ...
[Delayed Task 실행] GET /payments/{transactionKey}
→ PG: SUCCESS → 조건부 UPDATE → PAID
```

### 11.5 유령 결제 복구

```
[PgRouter] 1차 → 타임아웃 (PG에서는 결제 생성됨)
→ 타임아웃이므로 Fallback PG 전환하지 않음 (중복 결제 방지)
→ Payment(UNKNOWN)
→ [복구 경로 1] PG 콜백 수신 → PAID
→ [복구 경로 2] Delayed Task 10초 후 PG 조회 → PAID
→ [복구 경로 3] 배치 1분 후 PG 조회 → PAID
```

---

## 12. 트랜잭션 경계 설계

### 12.1 핵심 원칙

> **TX 분리 이유 = 외부 호출 격리 (도메인 분리 아님)**
> TX-0/TX-1/TX-2 분리는 MSA 준비가 아니다. PG 호출(외부 API)을 트랜잭션 밖으로 빼기 위함이다.
> PG 호출이 없었다면 TX-0 + TX-1 + TX-2 = 하나의 TX로 충분하다 (모놀리스).
> 분리 기준: "외부 시스템 호출이 TX 안에 있으면 커넥션 점유 → 고갈"
> (06 §21.3 근거)

**외부 호출(PG)은 트랜잭션 밖에서 수행한다.**

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. PG 호출을 트랜잭션 안에 포함 | 원자성 보장 (실패 시 자동 롤백) | **PG 지연(최대 4.5초) 동안 DB 커넥션 점유 → 커넥션 풀 고갈 위험** |
| **B. PG 호출을 트랜잭션 밖에서 수행** | **DB 커넥션 최소 점유, 외부 지연이 내부에 전파되지 않음** | 수동 상태 관리 필요 |

**결정: B**

**근거**: 대규모 트래픽 환경에서 외부 호출 지연이 DB 커넥션 풀을 고갈시키면
결제뿐 아니라 상품 조회, 주문 조회 등 전체 서비스가 마비된다.

### 12.2 트랜잭션 분리

> 가주문 + 쿠폰 선차감 추가로 TX-0 신설 (06 §20 근거)

```
[TX-0] 쿠폰 USED 처리 (CAS UPDATE 1건) → commit     ← 쿠폰 선차감 (쿠폰 없으면 생략)
[Redis] DECR(stock) + HSET(가주문, couponIssueId)     ← 재고 선차감 + 가주문 생성
[TX-1] Payment(REQUESTED) + Outbox(PENDING) → commit  ← 결제 기록
[PG 호출] CB → Retry → PG 요청                         ← 트랜잭션 없음
[TX-2] Payment 상태 업데이트 (PENDING 또는 UNKNOWN) → commit

... (콜백 수신 시) ...

[TX-3] Payment 확정 (PAID/FAILED) + Order 상태 + 쿠폰/재고 확정/복원 → commit
```

#### 커넥션 점유 시간 검증

```
TX-0: CAS UPDATE 1건 → ~5ms    (쿠폰 없는 주문은 생략)
TX-1: INSERT 2건 → ~10ms
PG 호출: 100ms~4.5초            ← 트랜잭션 없음, DB 커넥션 0개
TX-2: UPDATE 1건 → ~5ms
TX-3: UPDATE 2~3건 → ~10ms

총 DB 커넥션 점유: ~30ms (PG 지연과 무관)

초당 100건 기준:
  트랜잭션 분리: 100 × 30ms = 3 커넥션·초 (HikariCP 10개 → 30%)
  PG가 TX 안이면: 100 × 4.5초 = 450 커넥션·초 (HikariCP 10개 → 4500% → 즉시 고갈)
```

---

## 13. Transactional Outbox

### 13.1 목적

TX-1(Payment REQUESTED 저장) 커밋 후 PG 호출 전 서버 크래시 시,
PG 호출 의도를 명시적으로 보존하여 **수 초 내에 자동 재시도**.

### 13.2 변경된 트랜잭션 흐름

```
[TX-0] 쿠폰 USED 처리 → commit (쿠폰 없으면 생략)
[Redis] DECR(stock) + HSET(가주문)
[TX-1] Payment(REQUESTED) + PaymentOutbox(PENDING) 저장 → commit
[Outbox Poller: 5초 주기]
  PaymentOutbox(PENDING) 조회 → PG 호출 → PaymentOutbox(PROCESSED)
[TX-2] Payment 상태 업데이트 (PENDING 또는 UNKNOWN) → commit
```

### 13.3 Outbox 테이블

```sql
CREATE TABLE payment_outbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id   BIGINT NOT NULL,
    order_id     VARCHAR(50) NOT NULL,
    event_type   VARCHAR(30) NOT NULL,   -- 'PAYMENT_REQUEST'
    payload      TEXT NOT NULL,           -- PG 요청 Body (JSON)
    status       VARCHAR(20) NOT NULL,   -- 'PENDING' / 'PROCESSED' / 'FAILED'
    created_at   DATETIME NOT NULL,
    processed_at DATETIME,
    retry_count  INT DEFAULT 0
);
```

### 13.4 Outbox 폴러 동작

```
[스케줄러: 5초 주기]
  1. PaymentOutbox에서 status = 'PENDING' 조회
  2. 각 건에 대해:
     a. Payment 현재 상태 확인 → 이미 PAID/FAILED → Outbox PROCESSED (다른 경로로 해결됨)
     b. PG 상태 확인 (GET /payments?orderId=xxx) → 멱등성 보장
        - PG에 기록 있음 → transactionKey로 추적, Outbox PROCESSED
        - PG에 기록 없음 → PG 결제 요청 (POST) 실행
     c. retry_count 증가, 최대 3회 초과 시 Outbox FAILED + 운영 알림
```

### 13.5 Outbox + 배치 병행 구조

```
[1차 복구] Outbox 폴러 (5초 주기) — PG 호출 누락 즉시 재시도
[2차 복구] 배치 (1분 주기) — Outbox 폴러 자체 장애 시 최종 안전망
```

---

## 14. 주문-결제 상태 연동

### 13.1 주문 상태 전이 (기존 + 결제 추가)

```
기존: CREATED (주문 생성 = 완료)
변경: CREATED → PAYMENT_PENDING → PAID / CANCELLED
```

| 주문 상태 | 의미 | 전이 조건 |
|----------|------|----------|
| CREATED | 주문 생성, 재고 차감 완료 | 주문 생성 시 |
| PAYMENT_PENDING | 결제 진행 중 | 결제 요청 시 |
| PAID | 결제 완료 | 콜백 SUCCESS 수신 |
| CANCELLED | 주문 취소 | 결제 최종 실패 |

### 13.2 결제 실패 시 주문/재고/쿠폰 처리

> **선차감 원칙**: 재고, 쿠폰 모두 결제 전 선차감. 결제 실패 시 복원. (06 §19 근거)

| 시나리오 | 주문 상태 | 재고 | 쿠폰 |
|----------|----------|------|------|
| 결제 SUCCESS | PAID | 차감 유지 | 사용 유지 |
| 결제 FAILED (한도 초과, 잘못된 카드) | CANCELLED | **복원** | **복원** |
| 결제 UNKNOWN → 배치 확인 → SUCCESS | PAID | 차감 유지 | 사용 유지 |
| 결제 UNKNOWN → 배치 확인 → FAILED | CANCELLED | **복원** | **복원** |
| 결제 UNKNOWN → 배치 확인 → PG에 기록 없음 | CANCELLED | **복원** | **복원** |

---

## 15. 구현 계획 (단계별)

### Phase 1: 기반 구축

1. Payment 도메인 모델 (Entity, Repository, 상태 enum)
2. PG Client 인터페이스 + SimulatorPgClient (Feign) + Timeout 적용
3. PgRouter (Strategy Pattern) + Fallback PG 전환 로직
4. 결제 요청 API (`POST /api/v1/payments`) 기본 흐름
5. 가주문 모델 (ProvisionalOrder, couponIssueId 포함) + Redis Repository (기존 modules/redis 활용)
6. 가주문 TTL Jitter 적용 (±5분, 25~35분 분포 → 동시 만료 방지)

### Phase 2: Resilience 적용 (PG)

7. Resilience4j 의존성 추가
8. PG별 독립 Retry 설정 (수동 Retry 루프 + PG 상태 확인)
9. PG별 독립 CircuitBreaker 설정 (6개 인스턴스)
10. SlidingWindowRateLimiter 구현 (결제 요청: 50 req/sec)
11. PaymentRateLimiterInterceptor (AOP) + Micrometer 메트릭 등록
12. 배치 Rate Limiter 설정 (Resilience4j Fixed Window: 10 req/sec)
13. 최종 Fallback (UNKNOWN 상태) 구현
14. Health Check Probe + Progressive Backoff 구현

### Phase 3: Resilience 적용 (Redis)

15. Redis CB 1개 (`redis-write`만) + Lettuce commandTimeout 설정 (읽기 CB 불필요 — 06 §18)
16. Redis Fallback: DB 직접 주문 (ProvisionalOrderService + fallbackMethod)
17. 재고 예약: masterRedisTemplate DECR + DB UPDATE 이중 관리
18. Redis-DB 재고 정합성 배치 — Lua Script v2 (30초 주기, 원자적 보정)
19. 가주문 선제 만료 배치 — Proactive Expiry Scanner (30초 주기, TTL < 30초 감지)

### Phase 4: 콜백 + 상태 동기화

20. Callback Inbox (DLQ) 테이블 + 콜백 수신 API
21. 조건부 UPDATE 기반 상태 전이
22. 결제 실패 시 재고 복원 (Redis INCR + DB 복원) + 쿠폰 복원 (DB UPDATE)
23. Polling Hybrid (Delayed Task) 구현

### Phase 5: Outbox + 복구 + 대사

24. PaymentOutbox 테이블 + TX-1에 Outbox 저장 추가
25. Outbox 폴러 스케줄러 (5초 주기)
26. 배치 복구 (REQUESTED/PENDING/UNKNOWN, 1분 주기)
27. 수동 복구 API (`POST /api/v1/payments/{paymentId}/confirm`)
28. Local WAL (PG 응답 로컬 기록 + Recovery)
29. 대사 배치 [R1] PG ↔ Payment (1시간) — reconciled 플래그 + reconciliation_mismatch 기록
30. 대사 배치 [R2] Payment ↔ Order (1시간) — JOIN 쿼리 불일치 감지
31. 대사 배치 [R3] Payment ↔ Coupon (1시간) — 쿠폰 복원 누락 감지 + 자동 복원

### Phase 6: Multi-PG (Toss Sandbox)

32. TossSandboxPgClient 구현
33. Toss 전용 CB/Retry 설정
34. PgRouter에 Toss 등록 + Fallback 전환 로직 검증

### Phase 7: 테스트

35. 단위 테스트: 상태 전이, Fallback, 멱등성, 조건부 UPDATE
36. 통합 테스트: PG 연동 전체 흐름 (Simulator + Toss)
37. Redis 장애 테스트: redis-write CB Open → DB Fallback, 읽기 try-catch Fallback, 재고 정합성 Lua Script
38. 장애 시나리오 테스트: 타임아웃, CB Open, 콜백 미수신, Multi-PG 전환, 대사 배치

---

## 16. 패키지 구조

```
/interfaces/api/payment/
    PaymentV1Controller.java         # 결제 요청 API
    PaymentCallbackController.java   # 콜백 수신 (→ Callback Inbox)
    PaymentV1Dto.java
/application/payment/
    PaymentFacade.java               # 결제 유스케이스 조율
    PaymentRecoveryService.java      # Polling Hybrid + 수동 복구
/domain/payment/
    PaymentModel.java                # Entity
    PaymentStatus.java               # 내부 결제 상태 enum
    PaymentService.java
    PaymentRepository.java
    CallbackInbox.java               # DLQ Entity
    CallbackInboxRepository.java
    PaymentOutbox.java               # Outbox Entity
    PaymentOutboxRepository.java
/infrastructure/payment/
    PaymentRepositoryImpl.java
    PaymentJpaRepository.java
    CallbackInboxJpaRepository.java
    PaymentOutboxJpaRepository.java
    PaymentWalWriter.java            # Local WAL (파일 기반)
/infrastructure/pg/
    PgClient.java                    # PG 추상화 인터페이스
    PgRouter.java                    # Strategy 기반 PG 라우팅
    PgHealthChecker.java             # Health Probe (CB Open 중 PG 상태 확인)
    PgPaymentRequest.java
    PgPaymentResponse.java
    PgPaymentStatusResponse.java
    PgCallbackPayload.java
/infrastructure/pg/simulator/
    SimulatorPgClient.java           # PG Simulator Feign Client
    SimulatorPgConfig.java           # Simulator 전용 Timeout/CB/Retry
/infrastructure/pg/toss/
    TossSandboxPgClient.java         # Toss Payments Sandbox Client
    TossSandboxPgConfig.java         # Toss 전용 Timeout/CB/Retry
/infrastructure/redis/
    ProvisionalOrderRedisRepository.java  # 가주문 Redis 저장/조회/삭제 (masterRedisTemplate)
    StockReservationRedisRepository.java  # 재고 예약 Redis DECR/INCR (masterRedisTemplate)
    # RedisConfig, RedisProperties → modules/redis에 이미 존재 (건드리지 않음)
/infrastructure/resilience/
    SlidingWindowRateLimiter.java     # Sliding Window Counter (결제 요청)
    PaymentRateLimiterInterceptor.java # AOP 기반 Rate Limiter 적용
/infrastructure/scheduler/
    OutboxPollerScheduler.java       # Outbox 폴러 (5초)
    PaymentRecoveryScheduler.java    # 배치 복구 (1분)
    CallbackDlqScheduler.java       # DLQ 재처리
    WalRecoveryScheduler.java       # WAL Recovery
    StockReconcileScheduler.java          # Redis-DB 재고 정합성 — Lua Script v2 (30초)
    ProvisionalOrderExpiryScheduler.java  # 가주문 선제 만료 — Proactive Expiry Scanner (30초)
    PgPaymentReconciliationScheduler.java # [R1] PG ↔ Payment 대사 (1시간)
    PaymentOrderReconciliationScheduler.java  # [R2] Payment ↔ Order 대사 (1시간)
    PaymentCouponReconciliationScheduler.java # [R3] Payment ↔ Coupon 대사 (1시간)
/application/order/
    ProvisionalOrderService.java     # 가주문 생성 + Redis CB + DB Fallback + TTL Jitter
```

---

## 17. 의존성 추가

```kotlin
// Resilience4j
implementation("io.github.resilience4j:resilience4j-spring-boot3")
implementation("org.springframework.boot:spring-boot-starter-aop")

// Feign Client (PG Simulator)
implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

// Redis → modules/redis에 이미 포함 (추가 불필요)
// commerce-api가 implementation(project(":modules:redis")) 선언 완료

// Toss Payments Sandbox (REST 호출)
// Feign 또는 RestClient 사용 — 별도 SDK 불필요
// 인증: Test Secret Key (Base64 Authorization 헤더)
```

### 17.1 대사 테이블

```sql
-- 대사 배치 불일치 기록 (06 §22.4 근거)
CREATE TABLE reconciliation_mismatch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,    -- 'PG_PAYMENT', 'PAYMENT_ORDER', 'PAYMENT_COUPON'
    payment_id      BIGINT NOT NULL,
    our_status      VARCHAR(20) NOT NULL,
    external_status VARCHAR(20),             -- PG 상태 (R1) 또는 Order/Coupon 상태 (R2/R3)
    detected_at     DATETIME NOT NULL,
    resolved_at     DATETIME,
    resolution      VARCHAR(50),             -- 'AUTO_FIXED', 'MANUAL_FIXED', 'FALSE_ALARM'
    note            TEXT
);
```

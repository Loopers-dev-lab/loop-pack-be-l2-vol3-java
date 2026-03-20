# Resilience 설계 의사결정 기록

## 배경

PG(Payment Gateway) 연동은 외부 시스템 호출이다.
pg-simulator 스펙 기준:
- 요청 성공 확률: 60% (40% 확률로 500 에러 반환)
- 요청 지연: 100ms ~ 500ms
- 처리 지연(비동기 콜백): 1s ~ 5s

이 환경에서 PG 장애가 우리 서버 전체 장애로 번지지 않도록 Resilience 패턴을 검토했다.

---

## Resilience 패턴 검토

### 전체 패턴 비교

| 패턴 | 역할 | 적용 여부 | 판단 이유 |
|---|---|---|---|
| Timeout | PG 응답 무한 대기 방지 | ✅ | 스레드 고갈 방지를 위해 필수 |
| CircuitBreaker | PG 연속 실패 시 호출 차단 | ✅ | 장애 전파(Cascading Failure) 방지 |
| Retry | 일시적 실패 재시도 | ✅ | PG 40% 실패율 대응 |
| Fallback | 실패 시 대체 응답 | ✅ | CircuitBreaker OPEN 시 즉시 응답 |
| RateLimiter | 초당 inbound 요청 수 제한 | ❌ | 아래 참고 |
| Bulkhead | 서비스 간 스레드 풀 격리 | ❌ | 아래 참고 |

---

### RateLimiter 미적용 이유

RateLimiter는 **들어오는 요청(inbound)**을 초당 N개로 제한하는 패턴이다.

목적: 악의적 클라이언트나 급격한 트래픽 급증으로부터 서버를 보호한다.

**미적용 판단:**
- 현재 서비스는 소규모 단일 서버로 inbound 트래픽 급증 시나리오가 없다.
- outbound 제어(PG 호출 빈도 조절)는 CircuitBreaker가 이미 담당한다.
- 이 시점에서 RateLimiter 추가는 오버엔지니어링이다.

**적용이 의미있는 시점:**
- 공개 API로 외부 클라이언트가 무제한 호출 가능할 때
- PG사와 계약한 초당 호출 한도가 있을 때 (outbound RateLimit)

---

### Bulkhead 미적용 이유

Bulkhead는 **서비스 간 스레드 풀을 격리**해서 한 기능의 장애가 다른 기능 스레드를 잠식하지 못하게 한다.

예: 결제 스레드 풀 / 상품 조회 스레드 풀 분리 → 결제 부하가 상품 조회를 막지 않는다.

**미적용 판단:**
- 현재 프로젝트에서 PG 연동 외에 격리할 독립적인 외부 의존성이 없다.
- Bulkhead가 의미있으려면 "결제 요청이 상품 조회 스레드를 고갈시키는 상황"이 실제로 발생해야 한다.
- 단일 서버에서 하나의 외부 의존성(PG)만 있는 상황에서는 오버엔지니어링이다.

**적용이 의미있는 시점:**
- 결제 + 상품 + 배송 등 여러 외부 API가 동일 서버에서 동작할 때
- 특정 기능의 트래픽이 다른 기능의 스레드를 잠식하는 현상이 측정될 때

---

## Timeout 설정 근거 (latency budget 기반)

### k6 부하 테스트 결과

테스트 환경: 최대 50 VU, 2분간 단계별 부하
```
pg_success_rate:  2.03% (92 / 4510)         ← CircuitBreaker OPEN 영향
http_req_duration (성공 요청 기준):
  p(90) = 508ms
  p(95) = 527ms
  p(99) = 415ms  ← 성공 요청 p99
  max   = 614ms
```

### connectTimeout 산정

```
내부 네트워크(localhost) 기준 TCP 연결: 수 ms 이내
connectTimeout = 2s  (보수적 여유 포함)
```

### readTimeout 산정

```
pg-simulator 요청 지연: 100ms ~ 500ms
측정된 p99 = 415ms

1차 계산: p99 * 2 = 830ms → 1s
보수적 적용: 1.5s

현재 설정: 5s  ← k6 결과로 볼 때 과도하게 여유로움
→ 개선 여지: 1.5s ~ 2s로 줄여도 충분
```

**현재 5s를 유지한 이유:**
- pg-simulator 처리 지연(비동기 콜백)이 1s~5s로 콜백 수신과 혼동 가능성 고려
- 첫 설정은 보수적으로 잡고 운영 모니터링 후 점진적으로 줄이는 전략 채택

---

## CircuitBreaker 설정 및 관찰

### 설정값

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pg:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10          # 최근 10건 기준
        failure-rate-threshold: 50       # 50% 실패 시 OPEN
        wait-duration-in-open-state: 10s # 10초 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
```

### k6 관찰 결과

```
pg_circuit_open_rate: 97.96%  ← 50 VU 환경에서 거의 항상 OPEN 상태
```

**분석:**
- 50 VU 동시 요청 + PG 40% 실패율 → 빠르게 실패 10건 도달 → OPEN
- OPEN 상태에서 10s 대기 → HALF_OPEN 3건 시도 → 일부 실패 → 다시 OPEN 반복
- 결과: 정상 트래픽도 CircuitBreaker에 막힘

**개선 고민:**
- `sliding-window-size`를 늘리면 OPEN 진입이 느려짐 (더 많은 실패 허용)
- `failure-rate-threshold`를 높이면 더 많은 실패를 허용하고 OPEN을 늦춤
- 트래픽이 적은 환경(COUNT_BASED)에서는 임계값 설정이 중요

현재 설정은 pg-simulator 40% 실패율 환경에서 민감하게 반응하도록 의도했다.
실제 PG 장애 전파 방지 목적에는 적합하나, 정상 트래픽 손실이 크다.
운영 환경에서는 `sliding-window-size: 50`, `failure-rate-threshold: 60`으로 조정 검토 필요.

---

## 패턴 간 연결고리

```
Timeout (RestClient)
  → 응답 없는 PG 호출 차단 → 스레드 반환

Retry
  → 일시적 실패(503) 재시도 → 최대 3회
  → 단, 이미 처리된 결제가 있을 수 있으므로 PG 상태 조회 선행 필요 (멱등성 고려)

CircuitBreaker
  → Retry 실패 누적 시 OPEN → 즉시 Fallback 반환
  → 스레드 고갈 방지, P*T 유지

Fallback
  → PAYMENT_CIRCUIT_OPEN 에러 반환
  → 클라이언트가 재시도 가능/불가능 여부 판단 가능
```

각 패턴은 독립적이지 않다. Retry가 CircuitBreaker 안쪽에서 동작하고,
CircuitBreaker OPEN 시 Fallback이 동작하는 하나의 흐름으로 연결된다.

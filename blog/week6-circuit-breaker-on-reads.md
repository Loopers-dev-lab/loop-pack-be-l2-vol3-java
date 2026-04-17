서킷브레이커 적용 기준: 결제 복구 경로는 왜 차단하면 안 되는가


> > **TL;DR**: PG 결제 요청에 서킷브레이커를 걸었다. 당연히 상태 조회에도 걸었다. 그러자 복구 경로 세 개가 전부 멈췄다. 서킷브레이커는 "호출 자체를 차단"하는 도구다. 차단해도 되는 호출과 차단하면 안 되는 호출을 구분하지 않으면, 보호가 아니라 마비가 된다.

---

## 서킷브레이커는 try-catch가 아니다

서킷브레이커를 처음 도입할 때 한 가지 오해를 했다. "외부 호출을 안전하게 감싸는 것"이라고 생각한 것이다. 그러면 try-catch와 뭐가 다른가?

```
// try-catch: 호출은 한다. 실패하면 잡는다.
try {
    pgClient.requestPayment(request);    // ← 1초 타임아웃까지 대기
} catch (Exception e) {
    return fallbackResponse();
}

// 서킷브레이커: 호출 자체를 하지 않는다.
@CircuitBreaker(name = "pg-request")
public PgPaymentResponse requestPayment(request) {
    return pgClient.requestPayment(request);  // ← CB Open이면 여기에 도달하지 않음
}
```

try-catch는 호출을 하고 실패를 수습한다. 서킷브레이커는 **호출 자체를 막는다**. 이 차이가 왜 중요한가?

PG가 완전히 죽었다고 가정하자. 초당 100건의 결제 요청이 들어온다면 어떻게 될까?

| 보호 방식 | 동작 | 스레드 점유 |
|-----------|------|------------|
| try-catch (타임아웃 1초) | 100건 × 1초 대기 후 실패 | **100 스레드 × 1초 = 100 스레드·초** |
| CB Open | 100건 × 즉시 예외 (0ms) | **0 스레드·초** |

try-catch만으로 보호하면, PG가 죽어있는 동안 매 초 100개의 스레드가 1초씩 아무것도 하지 못한 채 대기한다. Retry까지 걸려 있으면 `100 × 3회 × 1초 = 300 스레드·초`다. 톰캣 기본 스레드 풀이 200개인 걸 생각하면, **1초 만에 스레드 풀이 고갈**된다.

서킷브레이커는 이걸 막는다. Open 상태에서는 PG에 요청을 보내지 않으니, 스레드가 대기하지 않는다. 실패할 게 뻔한 호출에 스레드를 낭비하지 않는 것 — 이게 서킷브레이커의 존재 이유다.

---

## 그래서 모든 외부 호출에 CB를 걸었다

이 원리를 이해하면 자연스러운 결론에 도달한다. "외부 호출에는 전부 CB를 걸자."

결제 시스템의 외부 호출은 크게 두 종류다.

```
[쓰기] POST /api/v1/payments     → PG에 결제를 요청한다
[읽기] GET  /api/v1/payments/:id  → PG에 결제 상태를 확인한다
```

둘 다 PG라는 외부 시스템을 호출한다. PG가 죽으면 둘 다 실패한다. 스레드 밀림 위험도 동일하다. CB를 거는 게 당연해 보인다.

처음에는 그렇게 설계했다.

```java
@CircuitBreaker(name = "pgSimulator-request")
public PgPaymentResponse requestPayment(PgPaymentRequest request) { ... }

@CircuitBreaker(name = "pgSimulator-status")
public PgPaymentStatusResponse getPaymentStatus(String transactionKey) { ... }
```

---

## 읽기에 CB를 걸었더니 복구가 멈췄다

문제는 "읽기"가 단순한 조회가 아니라는 데 있었다.

결제 시스템에서 상태 조회는 **복구 행위**다. 결제 요청이 타임아웃 나면 내부 상태는 `UNKNOWN`이 된다. 돈이 빠져나갔는지 아닌지 모르는 상태다. 이걸 해결하는 유일한 방법은 PG에 "이 결제 됐어?"라고 물어보는 것이다.

이 질문을 던지는 경로가 세 개 있다.

```
[복구 경로 1] Outbox Poller — 5초마다
  → Payment 생성 후 PG 호출 전에 장애 → Outbox에서 재시도
  → GET /payments?orderId=xxx  ← PG 읽기

[복구 경로 2] Polling Hybrid — 10초 후
  → PG 콜백 미수신 시 직접 확인
  → GET /payments/{transactionKey}  ← PG 읽기

[복구 경로 3] Batch Recovery — 1분마다
  → 위 두 경로가 다 실패한 건의 최종 안전망
  → GET /payments/{transactionKey}  ← PG 읽기
```

세 경로 모두 PG 상태 **읽기**에 의존한다. 이제 시나리오를 그려보자.

```
PG 결제 요청 대량 실패
→ pgSimulator-request CB Open     ← 쓰기 차단. 여기까진 정상.
→ pgSimulator-status CB도 Open    ← 읽기도 차단. 여기서 문제.

→ Outbox: "상태 확인해야 하는데 CB가 막는다" → 실패
→ Polling: "상태 확인해야 하는데 CB가 막는다" → 실패
→ Batch: "상태 확인해야 하는데 CB가 막는다" → 실패

→ UNKNOWN 상태 결제건 — 복구 불가
→ 초당 1000건 기준, 2초면 2000건의 결제가 확인 지연
```

보호하려고 건 CB가 복구를 막고 있었다.

---

## 쓰기 CB는 차단해도 괜찮다

왜 쓰기 CB는 문제가 안 되는지 짚어보자.

쓰기가 차단되면 **Fallback PG**가 있다.

```java
// PgRouter.java — Primary 실패 시 Fallback으로 전환
for (PgClient pgClient : pgClients) {
    try {
        return pgClient.requestPayment(request);
    } catch (Exception e) {
        if (isTimeoutException(e)) {
            // 타임아웃 → Fallback 전환 안 함 (중복 결제 방지)
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 타임아웃: " + pgClient.getProviderName() +
                " (Fallback 전환 불가 — 중복 결제 방지)");
        }
        // 그 외 → 다음 PG 시도
        lastException = e;
    }
}
```

쓰기 CB가 Open되면 → 즉시 예외 → PgRouter가 다음 PG로 라우팅. 비즈니스가 계속 돌아간다.

반면 읽기 CB가 Open되면? 상태 조회에는 Fallback PG라는 개념이 없다. 결제를 처리한 PG에만 물어볼 수 있다. Simulator PG로 결제했으면 Simulator PG에게만 "이거 됐어?"라고 물을 수 있다. **대체 경로가 없다.**

| 호출 종류 | CB Open 시 대안 | CB 적용 |
|-----------|-----------------|---------|
| 결제 요청 (POST) | Fallback PG로 전환 | **적용** |
| 상태 조회 (GET) | **없음** — 해당 PG만 알고 있음 | **미적용** |

---

## 그러면 읽기는 어떻게 보호하는가

CB를 빼면 스레드 밀림은 어떻게 막나? 다시 처음의 표를 보자.

| 보호 방식 | 스레드 점유 |
|-----------|------------|
| try-catch (타임아웃 1초) | 100 스레드·초 |
| CB Open | 0 스레드·초 |

읽기 호출의 트래픽 특성이 쓰기와 다르다. 쓰기는 사용자가 결제 버튼을 누를 때마다 발생한다 — 초당 100건. 읽기는 복구 배치에서 발생한다 — 분당 수십 건.

```
Outbox Poller:   5초 주기, 미처리 건만 조회 → 분당 ~12건
Polling Hybrid: 10초 후 1회 → 건당 1회
Batch Recovery:  1분 주기, 미처리 건 일괄 → 분당 수십 건
```

스레드 풀을 위협할 트래픽이 아니다. try-catch + 타임아웃 1초면 충분하다.

```java
// 최종 구현 — CB 없이, Timeout + try-catch만으로 보호
@Override
public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
    try {
        return feignClient.getPaymentStatus(transactionKey);
    } catch (Exception e) {
        log.warn("PG 상태 확인 실패: transactionKey={}, error={}",
            transactionKey, e.getMessage());
        return new PgPaymentStatusResponse("UNKNOWN", transactionKey, null);
    }
}
```

실패해도 `UNKNOWN`을 반환한다. 호출자는 "아직 모르겠다, 다음에 다시 물어봐야지"로 처리한다. 5초 후 Outbox가, 10초 후 Polling이, 1분 후 Batch가 다시 시도한다. 셋 중 하나는 성공한다.

---

## "그러면 DB에도 CB를 달아야 하나?"

외부 호출에 CB를 거는 이유가 "스레드 밀림 방지"라면, DB도 외부 시스템 아닌가? 네트워크를 타고, 장애가 날 수 있고, 느려질 수 있다.

결론부터 말하면, **안 단다**.

이유 1 — DB에는 이미 커넥션 풀이 있다.

```
HikariCP 설정:
  maximumPoolSize: 10
  connectionTimeout: 3000ms    ← 3초 안에 커넥션 못 얻으면 예외

효과: PG 타임아웃과 동일 — 무한 대기 불가
```

CB가 "실패할 게 뻔한 호출을 막아서 스레드를 아끼는" 도구라면, HikariCP의 `connectionTimeout`이 이미 그 역할을 한다. 커넥션을 3초간 못 얻으면 예외가 터진다. 스레드가 무한 대기하지 않는다.

이유 2 — DB에는 Fallback이 없다.

```
PG가 죽으면 → Fallback PG로 전환 (비즈니스 계속 동작)
DB가 죽으면 → ??? (Fallback DB? 없다.)
```

PG에 CB를 거는 건 "차단한 후 대안으로 전환"하기 위해서다. DB를 차단하면? 갈 곳이 없다. DB는 SOT(Source of Truth)다. 대체할 수 있는 것이 아니다.

이유 3 — DB 호출은 빠르다.

```
TX-0: CAS UPDATE 1건    → ~5ms
TX-1: INSERT 2건        → ~10ms
[PG 호출: 100ms~4.5초]  ← 트랜잭션 밖
TX-2: UPDATE 1건        → ~5ms

DB 커넥션 점유 시간: ~30ms
PG 호출 대기 시간: ~4.5초 (최악)
```

PG 호출은 트랜잭션 밖에서 수행한다. DB 커넥션을 점유하는 시간은 30ms 수준이다. 초당 100건이면 `100 × 0.03초 = 3 커넥션·초` — HikariCP 10개면 30% 사용률이다.

만약 PG 호출을 트랜잭션 안에서 했다면? `100 × 4.5초 = 450 커넥션·초` — **즉시 고갈**이다. DB에 CB를 다는 것보다, PG 호출을 트랜잭션 밖으로 빼는 것이 근본적인 해결이었다.

---

## CB를 걸어야 하는 세 가지 조건

돌아보면, CB를 적용할지 말지는 세 가지 질문으로 판단할 수 있었다.

| 질문 | Yes → CB | No → try-catch |
|------|----------|----------------|
| 실패 시 **대안 경로**가 있는가? | Fallback PG 전환 | 대안 없으면 차단 = 마비 |
| **대량 트래픽**이 밀릴 수 있는가? | 초당 100건 결제 | 분당 수십 건 복구 |
| 차단해도 **복구에 영향**이 없는가? | 쓰기 차단 → 복구와 무관 | 읽기 차단 → 복구 마비 |

세 질문에 모두 Yes면 CB를 건다. 하나라도 No면 try-catch + 타임아웃이 낫다.

최종 CB 인스턴스는 3개, 전부 쓰기 전용이다.

```
pgSimulator-request  → Simulator PG 결제 요청 (POST)
pgToss-request       → Toss PG 결제 요청 (POST)
redis-write          → Redis 재고 차감 + 가주문 저장
```

읽기에는 CB가 없다. DB에도 CB가 없다. 보호가 필요 없어서가 아니라, **CB라는 도구가 맞지 않아서**다.

---

## 돌아보며

서킷브레이커를 "외부 호출 보호 패턴"으로 일반화하면 함정에 빠진다. 모든 외부 호출에 기계적으로 CB를 걸게 되고, 읽기 CB가 복구 경로를 막는 상황을 뒤늦게 발견하게 된다.

서킷브레이커의 본질은 **"이 호출을 아예 하지 않겠다"**는 결정이다. 그 결정의 무게를 이해해야 한다. 호출을 안 하면 스레드는 아끼지만, 그 호출이 복구 경로였다면 시스템은 멈춘다.

try-catch는 "실패를 수습하는 도구"이고, 서킷브레이커는 "실패할 호출을 차단하는 도구"다. 두 도구는 용도가 다르다. 어떤 호출은 실패해도 시도해야 한다. 복구가 그렇다.

**보호의 대상이 아니라, 보호의 방식이 맞는지를 물어야 한다.**
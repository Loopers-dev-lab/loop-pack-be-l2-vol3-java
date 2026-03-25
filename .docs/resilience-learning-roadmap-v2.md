# Failure-Ready Systems 학습 로드맵

> 학습 순서: Timeout → 트랜잭션 경계 → 비동기 결제 흐름 → Circuit Breaker → Retry + Backoff → Fallback → 멱등성

---

## 1단계: Timeout — 장애 대응의 출발점

### 학습 목표
- Connection Timeout과 Read Timeout의 본질적 차이를 TCP 레벨에서 이해한다
- 타임아웃 부재 시 스레드/커넥션 고갈이 전체 시스템 마비로 이어지는 과정을 설명할 수 있다
- 적정 타임아웃 값을 정하는 판단 기준(p99 기반)을 이해한다
- **(추가) 소켓 타임아웃과 호출 타임아웃의 차이를 인지하고, 타임아웃이 너무 짧을 때의 위험성을 이해한다**
- **(추가) HTTP 커넥션 풀 관리를 통한 성능 최적화 방법을 익힌다**

### 학습 내용

#### 1-1. Connection Timeout vs Read Timeout
- **Connection Timeout**: TCP 3-way handshake 완료까지의 대기 시간. 상대 서버가 아예 죽었거나 네트워크가 끊긴 경우에 걸린다.
- **Read Timeout**: 연결 수립 후 응답 데이터를 받기까지의 대기 시간. 서버가 살아있지만 처리가 느린 경우에 걸린다.
- 실무 장애의 대부분은 Read Timeout 영역에서 발생한다.

#### 1-2. 타임아웃 없을 때의 연쇄 장애
- Tomcat 기본 스레드 풀(200개) + PG 30초 지연 → 200개 스레드 30초 내 고갈
- HikariCP 커넥션 풀도 동시 점유 → PG와 무관한 DB 조회까지 영향
- 이 과정을 숫자로 직접 계산해보기

#### 1-3. 적정 타임아웃 값 산정 및 주의사항
- 평소 응답 p99 기준 + 여유분
- PG 시뮬레이터 100ms~500ms 지연이면 Read Timeout 1~2초가 합리적
- "왜 이 값을 선택했는가"를 설명할 수 있어야 한다
- **(추가) 타임아웃이 너무 짧을 때의 위험**: 연동 서비스가 정상 처리했음에도 타임아웃 에러가 발생하여, 고객은 결제되었으나 상품은 구매하지 못하는 불쾌한 상황이 발생할 수 있다.
- **(추가) 소켓 타임아웃 vs 호출 타임아웃**: Apache HttpClient는 패킷 단위의 소켓 타임아웃을, OkHttp는 전체 요청의 호출 타임아웃을 설정하므로 사용 중인 라이브러리의 기준을 명확히 알아야 한다.

#### 1-4. (추가) HTTP 커넥션 풀 최적화
- **풀 크기**: 연동 서비스의 성능 한계를 고려하여 설정 (무턱대고 늘리면 상대 서버에 부하).
- **대기 시간**: 풀에서 커넥션을 얻기까지 대기하는 시간으로, 수 초 이내의 짧은 시간으로 설정.
- **유지 시간(Keep-Alive)**: 연동 서버의 유지 시간 정책에 맞춰 적절히 설정하여 끊어진 커넥션 사용으로 인한 에러 방지.

### 실습 과제
- [ ] RestTemplate에서 `setConnectTimeout`, `setReadTimeout` 설정 후 `SocketTimeoutException` 확인
- [ ] FeignClient에서 `connectTimeout`, `readTimeout` 설정
- [ ] 타임아웃 없이 동시 요청 100개 발생 → 스레드 고갈 직접 확인
- [ ] HikariCP `connection-timeout`, Redis `timeout` 설정

### 참고 문서

| 구분 | 링크 |
|------|------|
| Spring Cloud OpenFeign 공식 문서 | https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/ |
| Baeldung - Feign Client Timeout 설정 | https://www.baeldung.com/feign-timeout |
| Spring Boot Timeout 종합 가이드 | https://oneuptime.com/blog/post/2025-12-22-spring-boot-connection-timeout/view |
| HikariCP 공식 GitHub | https://github.com/brettwooldridge/HikariCP |
| Lettuce (Redis) 공식 문서 | https://lettuce.io/core/release/reference/ |

---

## 2단계: 트랜잭션 경계와 외부 호출

### 학습 목표
- 외부 호출이 `@Transactional` 안에 있으면 안 되는 이유를 DB 커넥션 점유 관점에서 증명한다
- 세 가지 실패 시나리오에서 상태 불일치가 발생하는 과정을 정확히 그릴 수 있다
- 보상 트랜잭션(Compensating Transaction)의 개념과 필요성을 이해한다
- **(추가) 외부 연동 지연이 DB 커넥션 풀을 어떻게 포화시키는지 시나리오 기반으로 이해한다**

### 학습 내용

#### 2-1. 외부 호출 + @Transactional의 문제
- PG 호출 3초 동안 DB 커넥션 점유 → 커넥션 풀 고갈
- PG와 무관한 단순 조회 API까지 전부 영향받는 연쇄 장애
- 해법: 외부 호출은 트랜잭션 밖에서 수행
- **(추가) 커넥션 풀 포화 상세 시나리오**: DB 쿼리 자체는 0.1초밖에 안 걸려도, 외부 연동이 4.8초 지연되면 커넥션은 총 5초 동안 점유된다. 평소 초당 1건의 요청이 5초 지연으로 쌓이면 커넥션 풀(예: 크기 5)이 순식간에 포화된다.

#### 2-2. 세 가지 핵심 실패 시나리오 및 후처리
1. **내부 커밋 성공 → 외부 호출 실패**: 주문 "결제완료" but PG는 미결제
2. **외부 호출 성공 → 내부 커밋 실패**: PG에서 돈 빠짐 but 주문 미생성
3. **외부 호출 성공 → 응답 유실**: 우리는 실패 판단, PG는 성공 처리
- **(추가) 응답 유실(읽기 타임아웃) 시 후처리**: 읽기 타임아웃은 외부 서비스가 실제로는 성공적으로 처리했을 가능성이 높으므로, 데이터 불일치 방지를 위해 일정 주기마다 상태를 대조하거나, 성공 확인 API / 취소 API를 호출하는 로직이 반드시 수반되어야 한다.

각 시나리오에서 "진실의 원천(Source of Truth)"이 어디인지 생각해볼 것

#### 2-3. 2단계 커밋 방식
- 외부 호출 전 내부 상태를 `PENDING`으로 먼저 커밋
- 외부 호출 결과에 따라 상태 갱신
- 어느 시점에 실패해도 `PENDING` 건을 나중에 복구 가능

#### 2-4. 보상 트랜잭션 개념
- 외부 성공 후 내부 실패 시 → 외부에 취소 요청 (결제 취소 API 등)
- 보상 트랜잭션 자체도 실패할 수 있음 → 멱등성 필요
- Saga 패턴과의 관계

### 실습 과제
- [ ] PG 호출 후 의도적으로 `RuntimeException` 발생 → 내부 롤백 확인
- [ ] PG 결제 조회 API로 "PG 성공 / 내부 실패" 불일치 상태 직접 확인
- [ ] 외부 호출을 `@Transactional` 밖으로 분리하는 구조 설계

### 참고 문서

| 구분 | 링크 |
|------|------|
| Microsoft - Compensating Transaction 패턴 | https://learn.microsoft.com/en-us/azure/architecture/patterns/compensating-transaction |
| Microsoft - Saga 패턴 | https://learn.microsoft.com/en-us/azure/architecture/patterns/saga |
| Baeldung - Saga Pattern in Microservices | https://www.baeldung.com/cs/saga-pattern-microservices |
| microservices.io - Saga Pattern | https://microservices.io/patterns/data/saga.html |
| Wikipedia - Compensating Transaction | https://en.wikipedia.org/wiki/Compensating_transaction |

---

## 3단계: 비동기 결제 흐름 이해

### 학습 목표
- 요청-응답 분리 구조(비동기 결제)의 흐름을 정확히 이해한다
- 상태 머신(State Machine) 기반으로 주문/결제 상태를 설계할 수 있다
- 콜백 유실 시 폴링/수동 조회로 상태를 복구하는 메커니즘을 구현할 수 있다

### 학습 내용

#### 3-1. 비동기 결제 구조
```
Client → Commerce API → PG (POST /payments)
         ← 즉시 응답 (트랜잭션 ID, "접수됨")
         
PG 내부 처리 (1s~5s) → 콜백 호출 (POST /callback)
         → Commerce API (결제 결과 수신)
```
- POST 요청의 200 응답은 **"결제가 됐다"가 아니라 "접수했다"**
- 이 차이를 놓치면 전체 설계가 틀어짐

#### 3-2. 상태 머신 설계
```
주문 생성 → PAYMENT_PENDING → PAID (콜백 성공)
                             → PAYMENT_FAILED (콜백 실패/한도초과/잘못된카드)
                             → PAYMENT_TIMEOUT (일정 시간 내 콜백 미수신)
```
- **허용되지 않는 전이도 정의**: `PAID → PAYMENT_PENDING`은 절대 불가
- 상태 전이를 코드로 방어 (`if (currentStatus != PENDING) throw ...`)

#### 3-3. 콜백 미수신 대응
- 콜백이 안 오는 이유: 네트워크 문제, 콜백 서버 다운, PG 버그
- **폴링 방식**: 일정 시간 지난 `PENDING` 건을 주기적으로 PG 결제 조회 API로 확인
- **수동 조회 API**: 관리자가 수동 트리거 가능한 엔드포인트 제공

### 실습 과제
- [ ] 상태 전이 다이어그램 먼저 그리기 (각 전이마다 "실패하면?" 표기)
- [ ] 콜백 수신 엔드포인트(POST /api/v1/payments/callback) 구현
- [ ] PG 결제 조회 API(`GET /api/v1/payments/{transactionId}`)로 상태 복구 로직 구현
- [ ] 의도적으로 콜백을 안 보내는 시나리오에서 복구 동작 확인

### 참고 문서

| 구분 | 링크 |
|------|------|
| PG 시뮬레이터 API 스펙 | 과제 제공 `pg-simulator` 모듈 |
| Martin Fowler - State Machine | https://martinfowler.com/eaaDev/State.html |
| Baeldung - Spring State Machine | https://www.baeldung.com/spring-state-machine |

---

## 4단계: Circuit Breaker

### 학습 목표
- Closed → Open → Half-Open 상태 전이 원리를 설정값 기반으로 예측할 수 있다
- Sliding Window 기반 실패율 계산을 직접 할 수 있다
- Slow Call을 실패로 간주하는 설정의 의미와 중요성을 이해한다
- **(추가) 벌크헤드(Bulkhead) 패턴을 통한 자원 격리와 빠른 실패(Fail Fast)의 이점을 이해한다**

### 학습 내용

#### 4-1. 상태 전이 원리
| 상태 | 설명 | 전이 조건 |
|------|------|-----------|
| **Closed** | 정상 상태, 모든 호출 통과 | 실패율이 threshold 초과 → Open |
| **Open** | 모든 호출 즉시 차단 (`CallNotPermittedException`) | `waitDurationInOpenState` 경과 → Half-Open |
| **Half-Open** | 제한된 수의 테스트 호출만 허용 | 성공 → Closed, 실패 → Open |

#### 4-2. Sliding Window 실패율 계산
- **COUNT_BASED**: 최근 N개 요청 중 실패 비율
  - `slidingWindowSize: 10`, `failureRateThreshold: 50` → 최근 10개 중 5개 이상 실패 시 Open
- **TIME_BASED**: 최근 N초 동안의 실패 비율
- 직접 손으로 계산: 요청 10개 중 5개 실패 → Open → 10초 대기 → Half-Open → 2개 중 1개 성공 → Closed

#### 4-3. Slow Call 설정
- `slow-call-duration-threshold: 2s` → 2초 이상 걸린 응답을 "느린 호출"로 분류
- `slow-call-rate-threshold: 50` → 느린 호출 비율 50% 초과 시 Open
- PG가 "에러는 안 주지만 엄청 느린 상태"에서도 보호 가능

#### 4-4. Open 상태의 동작 및 방어 패턴
- 외부 호출을 아예 안 하고 즉시 `CallNotPermittedException` 발생
- fallback 실행 → 사용자에게 "잠시 후 다시 시도해주세요" 응답
- 죽은 서버에 계속 요청을 보내는 것 자체가 자원 낭비이자 장애 확산
- **(추가) 동시 요청 제한 (벌크헤드 패턴)**: 연동 서비스가 처리 가능한 한계를 초과하여 요청을 보내지 않도록 제한하여, 특정 연동의 장애가 다른 정상 기능에 영항을 주지 않도록 격리한다.
- **(추가) 빠른 실패 (Fail Fast)**: 서킷 브레이커가 열려 즉시 에러를 리턴함으로써, 불필요한 자원 대기를 방지하고 전체 서비스의 안정성을 유지한다.

### 실습 과제
- [ ] Resilience4j CircuitBreaker 설정 및 적용
- [ ] 이벤트 리스너로 상태 전이 로그 확인: `circuitBreaker.getEventPublisher().onStateTransition(...)`
- [ ] Actuator 엔드포인트로 실시간 상태 모니터링: `/actuator/circuitbreakers`
- [ ] PG 시뮬레이터의 60% 성공률 환경에서 Circuit Breaker 동작 관찰

### 참고 문서

| 구분 | 링크 |
|------|------|
| **Resilience4j 공식 - CircuitBreaker** | https://resilience4j.readme.io/docs/circuitbreaker |
| **Resilience4j 공식 - Spring Boot 통합** | https://resilience4j.readme.io/docs/getting-started-3 |
| Resilience4j GitHub | https://github.com/resilience4j/resilience4j |
| Baeldung - Resilience4j with Spring Boot | https://www.baeldung.com/spring-boot-resilience4j |
| Baeldung - Resilience4j 가이드 | https://www.baeldung.com/resilience4j |
| Spring Cloud CircuitBreaker 공식 | https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/ |
| Spring Cloud CircuitBreaker + Resilience4j | https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/spring-cloud-circuitbreaker-resilience4j.html |

---

## 5단계: Retry + Backoff

### 학습 목표
- Retryable vs Non-retryable 예외를 구분하는 기준을 세울 수 있다
- Exponential Backoff + Jitter의 필요성을 Retry Storm 관점에서 설명할 수 있다
- Retry와 Circuit Breaker의 조합 순서(Aspect Order)를 이해하고 설정할 수 있다

### 학습 내용

#### 5-1. Retryable vs Non-retryable 예외 구분
| 재시도 O (일시적 장애) | 재시도 X (영구적 장애) |
|------------------------|----------------------|
| `SocketTimeoutException` | HTTP 400 (잘못된 요청) |
| `ConnectException` | HTTP 401 (인증 실패) |
| HTTP 503 (서버 과부하) | 비즈니스 에러 ("잔액 부족") |
| `RetryableException` | `IllegalArgumentException` |

- **(추가) 재시도 가능 조건 주의**: 연결 타임아웃(`ConnectException`)은 상대방이 아직 요청을 받지 못해 안전하지만, 읽기 타임아웃(`SocketTimeoutException`)은 이미 처리 중일 수 있으므로 **반드시 멱등성이 보장되는 기능에서만 재시도**해야 한다 (예: 포인트 중복 차감 위험 방지).

#### 5-2. Backoff 전략
- **Fixed Backoff**: 1초 → 1초 → 1초 (단순하지만 과부하 시 비효율)
- **Exponential Backoff**: 1초 → 2초 → 4초 (서버 회복 시간 확보)
- **Exponential Random Backoff**: 위에 랜덤 jitter 추가 (Thundering Herd 방지)

```yaml
# Resilience4j 설정 예시
resilience4j:
  retry:
    instances:
      pgRetry:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        enable-randomized-wait: true
        randomized-wait-factor: 0.5
```

#### 5-3. Retry Storm 문제
- PG가 503을 뱉는 상황에서 모든 클라이언트가 3번씩 재시도 → 트래픽 3배
- **(추가) 재시도 폭풍 안티패턴**: 이미 성능이 느려진 연동 서버에 재시도로 인해 같은 요청을 두 배로 보내게 되면 서버 성능은 더욱 악화되어 장애가 가속화된다.
- Circuit Breaker와 조합이 필수

#### 5-4. Retry + Circuit Breaker Aspect 순서
- Resilience4j 기본 순서: `Retry( CircuitBreaker( 실제호출 ) )`
  - Retry가 가장 바깥 → Circuit Breaker Open이어도 Retry가 재시도 시도
- **권장 순서**: `CircuitBreaker( Retry( 실제호출 ) )`
  - Retry가 다 끝난 후 최종 결과를 Circuit Breaker가 판단
  - 설정: `retryAspectOrder`를 `circuitBreakerAspectOrder`보다 크게

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1
  retry:
    retryAspectOrder: 2  # 높은 값 = 높은 우선순위 = 먼저 실행
```

#### 5-5. 실질 성공률 계산
- PG 요청 성공률 60%, 재시도 3회 시:
  - 최종 실패 확률 = 0.4³ = 0.064 (6.4%)
  - 최종 성공률 = 1 - 0.064 = **93.6%**

### 실습 과제
- [ ] Resilience4j Retry 설정 및 적용 (`@Retry` 어노테이션)
- [ ] `retry-exceptions`에 재시도 대상 예외 명시
- [ ] Exponential Backoff 설정 후 로그로 재시도 간격 확인
- [ ] Retry + CircuitBreaker Aspect Order 설정
- [ ] PG 60% 성공률에서 재시도 3회 시 실질 성공률 측정

### 참고 문서

| 구분 | 링크 |
|------|------|
| **Resilience4j 공식 - Retry** | https://resilience4j.readme.io/docs/retry |
| Resilience4j Spring Boot - Aspect 순서 | https://resilience4j.readme.io/docs/getting-started-3 |
| Baeldung - Resilience4j with Spring Boot | https://www.baeldung.com/spring-boot-resilience4j |
| AWS - Exponential Backoff and Jitter | https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/ |
| DEV.to - Retry + CircuitBreaker 조합 주의점 | https://dev.to/akdevcraft/when-resilience-backfires-retry-and-circuit-breaker-in-spring-boot-10m |

---

## 6단계: Fallback 전략

### 학습 목표
- Fallback이 "에러 메시지 변경"이 아니라 "시스템이 할 수 있는 최선의 대안 동작"임을 이해한다
- 수준별 Fallback 전략(단순 안내 → 상태 저장 → 부분 서비스)을 설계할 수 있다
- Fallback에서 하면 안 되는 것을 알고 지킨다
- **(추가) 연동 서비스 이중화를 통한 가용성 확보 전략을 이해한다**

### 학습 내용

#### 6-1. Fallback 수준별 전략

| 수준 | 전략 | 예시 |
|------|------|------|
| Level 1 | 안내 메시지 반환 | "현재 결제 처리가 지연되고 있습니다" |
| Level 2 | PENDING 상태 저장 후 재처리 | 결제를 PENDING으로 저장, 나중에 PG 복구 시 처리 |
| Level 3 | 부분적 서비스 유지 | 결제 불가하지만 주문 조회는 가능 (캐시 활용) |

#### 6-2. Fallback 구현 패턴
```java
@CircuitBreaker(name = "pgCircuit", fallbackMethod = "paymentFallback")
@Retry(name = "pgRetry", fallbackMethod = "paymentFallback")
public PaymentResponse requestPayment(PaymentRequest request) {
    return pgClient.requestPayment(request);
}

// Fallback: 가볍고 확실하게 동작해야 한다
public PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
    // PENDING 상태로 저장 (나중에 복구)
    paymentRepository.savePending(request);
    return new PaymentResponse("결제 대기 상태", PaymentStatus.PENDING);
}
```

#### 6-3. Fallback에서 절대 하면 안 되는 것
- Fallback 안에서 또 다른 외부 호출 금지
- 무거운 DB 쿼리 금지
- Fallback 자체가 장애 원인이 되면 안 된다
- 가볍고, 빠르고, 확실하게

#### 6-4. Graceful Degradation 및 서비스 이중화
- 100% 동작 or 0% 동작이 아니라, 일부 기능 포기하면서 핵심 유지
- 결제 불가 → 주문 거부 (X) → "결제 대기" 상태로 접수 (O)
- PG 복구 후 PENDING 건 일괄 처리
- **(추가) 연동 서비스 이중화**: 결제와 같이 멈추면 매출에 직결되는 '핵심 기능'의 경우, 메인 결제 서비스에 장애가 발생하더라도 백업 결제 서비스로 자동 전환되도록 이중화 구조를 설계하여 대응한다 (도입 전 핵심 여부와 비용 고려 필수).

### 실습 과제
- [ ] `fallbackMethod` 구현 (Circuit Breaker + Retry 양쪽)
- [ ] Fallback에서 PENDING 상태 저장 로직 구현
- [ ] PG 전체 장애 시에도 내부 시스템은 정상 응답하는지 확인
- [ ] Fallback 처리된 PENDING 건을 나중에 복구하는 메커니즘 구현

### 참고 문서

| 구분 | 링크 |
|------|------|
| Resilience4j 공식 - Fallback | https://resilience4j.readme.io/docs/getting-started-3 |
| MSA Fallback Pattern | https://badia-kharroubi.gitbooks.io/microservices-architecture/content/patterns/communication-patterns/fallback-pattern.html |
| Martin Fowler - Circuit Breaker | https://martinfowler.com/bliki/CircuitBreaker.html |

---

## 7단계: 멱등성 (Idempotency)

### 학습 목표
- "같은 요청을 N번 보내도 결과가 1번과 같다"를 보장하는 설계를 할 수 있다
- 멱등키(Idempotency Key) 기반 중복 요청 방어 메커니즘을 구현할 수 있다
- 재시도가 안전하려면 멱등성이 전제 조건임을 이해한다

### 학습 내용

#### 7-1. 멱등성이 없으면 재시도가 위험한 이유
- 결제 재시도 → PG에서 두 번 다 성공 → 이중 결제
- 중복 요청 발생 원인: 사용자 중복 클릭, 네트워크 타임아웃 후 재시도, 로드밸런서 자동 재시도

#### 7-2. 멱등키(Idempotency Key) 설계
```
Client → POST /payments
         Header: Idempotency-Key: {UUID}
         
Server:
  1. Idempotency Key로 기존 처리 여부 확인
  2-a. 이미 처리됨 → 이전 결과 반환 (새로 처리 X)
  2-b. 미처리 → 정상 처리 후 결과 저장
```

#### 7-3. 설계 시 고려사항
| 항목 | 선택지 | 고려사항 |
|------|--------|---------|
| 저장소 | DB vs Redis | DB는 영속성 보장, Redis는 빠르지만 유실 가능 |
| TTL | 24시간~48시간 | 재시도 윈도우와 맞춰야 함 |
| 동시 요청 | 분산 락 vs UNIQUE 제약조건 | 같은 키로 동시에 들어오면? |
| 요청 검증 | 키만 확인 vs 페이로드도 확인 | 같은 키 + 다른 내용이면 거부 |

#### 7-4. 과제에서의 적용
- PG 시뮬레이터가 `orderId`를 받으므로, 같은 `orderId`로 중복 결제 방어 필요
- PG 요청 전 "이 주문에 대해 이미 진행 중인 결제가 있는지" 체크
- `IN_PROGRESS` 상태의 결제가 이미 있으면 새 요청 차단

### 실습 과제
- [ ] 결제 요청 전 중복 결제 체크 로직 구현
- [ ] orderId 기반 멱등성 보장 (같은 주문에 대한 중복 결제 방지)
- [ ] 동시에 같은 주문의 결제 요청이 들어오는 경우 방어
- [ ] 멱등키 저장소 설계 (DB UNIQUE 제약조건 or Redis SETNX)

### 참고 문서

| 구분 | 링크 |
|------|------|
| **Stripe 공식 블로그 - Idempotency** | https://stripe.com/blog/idempotency |
| **Stripe API - Idempotent Requests** | https://docs.stripe.com/api/idempotent_requests |
| **Brandur - Idempotency Keys in Postgres** | https://brandur.org/idempotency-keys |
| Square - Idempotency 공식 문서 | https://developer.squareup.com/docs/build-basics/common-api-patterns/idempotency |
| Zuplo - Idempotency Keys 구현 가이드 | https://zuplo.com/learning-center/implementing-idempotency-keys-in-rest-apis-a-complete-guide |

---

## 종합 참고 자료

### 핵심 공식 문서 (반드시 읽기)

| 우선순위 | 문서 | 링크 |
|----------|------|------|
| ⭐⭐⭐ | Resilience4j 공식 문서 | https://resilience4j.readme.io/ |
| ⭐⭐⭐ | Resilience4j Spring Boot 통합 | https://resilience4j.readme.io/docs/getting-started-3 |
| ⭐⭐⭐ | Stripe - Idempotency 설계 | https://stripe.com/blog/idempotency |
| ⭐⭐ | Spring Cloud OpenFeign | https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/ |
| ⭐⭐ | Spring Cloud CircuitBreaker | https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/ |
| ⭐⭐ | Microsoft - Compensating Transaction | https://learn.microsoft.com/en-us/azure/architecture/patterns/compensating-transaction |
| ⭐⭐ | Microsoft - Saga 패턴 | https://learn.microsoft.com/en-us/azure/architecture/patterns/saga |

### Baeldung 튜토리얼 (실습 참고)

| 주제 | 링크 |
|------|------|
| Resilience4j with Spring Boot | https://www.baeldung.com/spring-boot-resilience4j |
| Resilience4j 종합 가이드 | https://www.baeldung.com/resilience4j |
| Feign Client Timeout | https://www.baeldung.com/feign-timeout |
| Saga Pattern | https://www.baeldung.com/cs/saga-pattern-microservices |

### 심화 읽기

| 주제 | 링크 |
|------|------|
| Brandur - Idempotency Keys in Postgres | https://brandur.org/idempotency-keys |
| Martin Fowler - Circuit Breaker | https://martinfowler.com/bliki/CircuitBreaker.html |
| microservices.io - Saga Pattern | https://microservices.io/patterns/data/saga.html |
| AWS - Exponential Backoff and Jitter | https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/ |

---

## 7개 주제의 연결 고리

```
Timeout (장애 감지) 
  → Retry (일시적 장애 대응) 
    → Circuit Breaker (반복 실패 차단) 
      → Fallback (차단 상태에서 사용자 경험 보호)

이 전체 흐름이 안전하려면:
  - 외부 호출은 트랜잭션 밖에 있어야 하고 (트랜잭션 경계)
  - 재시도가 안전하려면 멱등성이 보장되어야 하며 (멱등성)
  - 비동기 결제 흐름은 상태 머신으로 관리되어야 한다 (비동기 결제)
```
# 04. Resilience 설계

## 목적

PG 시스템 장애 및 지연에 대응하여 commerce-api 서비스 전체가 무너지지 않도록 보호한다.
Resilience4j 라이브러리를 활용하여 Timeout, CircuitBreaker, Fallback, (Optional) Retry를 적용한다.

---

## Resilience4j 소개

Spring Boot 3 환경에서 사용하는 경량 장애 허용 라이브러리.
각 패턴을 데코레이터(어노테이션)로 적용할 수 있다.

### 의존성 추가

```kotlin
// build.gradle.kts (commerce-api)
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
implementation("org.springframework.boot:spring-boot-starter-aop")  // 어노테이션 기반 사용
```

---

## 패턴별 분석

### 1. Timeout (Must-Have)

**목적**: PG 응답 지연 시 빠르게 실패하여 스레드 점유 방지

**RestTemplate Timeout vs Resilience4j TimeLimiter 차이**

| 항목 | RestTemplate Timeout | Resilience4j TimeLimiter |
|------|---------------------|-------------------------|
| 적용 대상 | HTTP 커넥션/읽기 레벨 | 메서드 실행 레벨 |
| 설정 위치 | RestTemplate Bean | 어노테이션 또는 설정 파일 |
| 메트릭 | 없음 | Prometheus 자동 연동 |
| 조합 | 단독 | CircuitBreaker와 조합 가능 |

**두 가지 모두 적용하는 것이 좋다**:
- RestTemplate Timeout: HTTP 레벨 보호 (네트워크 이상)
- TimeLimiter: 비즈니스 레벨 보호 (전체 메서드 실행 시간 제한)

```yaml
resilience4j:
  timelimiter:
    instances:
      pgPayment:
        timeout-duration: 3s
        cancel-running-future: true
```

---

### 2. CircuitBreaker (Must-Have)

**목적**: PG 시스템 장애 시 요청을 차단하여 연쇄 실패 방지

**상태 전이**

```
CLOSED (정상) → 실패율 임계값 초과 → OPEN (차단)
                                        │
                                   대기 시간 경과
                                        │
                                        ▼
                                  HALF_OPEN (시험)
                                     │        │
                                  성공 →  CLOSED
                                  실패 →  OPEN
```

**설정 시 주의점**

PG 시스템의 기본 실패율이 40%(요청 성공률 60%)이므로, CircuitBreaker 임계값 설정이 까다롭다.

```
문제: failureRateThreshold = 50으로 설정하면?
  → PG 기본 실패율(40%)과 가까워 정상 상황에서도 OPEN될 수 있음

해결: "PG 장애"와 "정상적인 결제 거절"을 구분해야 한다
```

**실패 판정 기준 설계**

| 상황 | CircuitBreaker 실패로 카운트? | 이유 |
|------|-------------------------------|------|
| 타임아웃 (ReadTimeout) | ✅ Yes | PG 시스템 지연 → 장애 징후 |
| 커넥션 실패 (ConnectException) | ✅ Yes | PG 시스템 다운 |
| HTTP 5xx 응답 | ✅ Yes | PG 서버 에러 |
| HTTP 4xx 응답 (잘못된 요청) | ❌ No | 클라이언트 문제, PG는 정상 |
| 결제 거절 (한도초과, 잘못된카드) | ❌ No | 비즈니스 실패, PG는 정상 동작 중 |

**→ recordExceptions에 인프라 에러만 등록하고, 비즈니스 실패는 ignoreExceptions로 처리**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgPayment:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10           # 최근 10건 기준
        failure-rate-threshold: 50        # 50% 이상 실패 시 OPEN
        wait-duration-in-open-state: 30s  # OPEN 후 30초 대기
        permitted-number-of-calls-in-half-open-state: 3  # HALF_OPEN에서 3건 시험
        record-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignore-exceptions:
          - com.loopers.support.error.CoreException  # 비즈니스 예외는 무시
```

---

### 3. Fallback (Must-Have)

**목적**: CircuitBreaker OPEN 시 사용자에게 적절한 응답 반환

**Fallback 전략 선택지**

| 방안 | 설명 | 적합한 상황 |
|------|------|-------------|
| A. 즉시 에러 반환 | "결제 시스템 점검 중" 메시지 | 단순하지만 사용자 경험 떨어짐 |
| B. PENDING 저장 후 재시도 안내 | 결제 레코드를 만들되 PG 호출 생략, 복구 시 재시도 | PG 복구 후 이어서 처리 가능 |
| C. 대기열 저장 | 요청을 큐에 넣고 나중에 처리 | 가장 유연하지만 복잡도 높음 |

**추천: A안 (즉시 에러 반환)** — 과제 범위에서는 충분하며, 사용자에게 명확한 피드백 제공

```java
@CircuitBreaker(name = "pgPayment", fallbackMethod = "paymentFallback")
public PgPaymentResponse requestPayment(PgPaymentRequest request) {
    return pgClient.requestPayment(request);
}

private PgPaymentResponse paymentFallback(PgPaymentRequest request, Exception e) {
    throw new CoreException(ErrorType.INTERNAL_ERROR,
        "결제 시스템이 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");
}
```

---

### 4. Retry (Nice-To-Have)

**목적**: 일시적 실패 시 자동 재시도

**주의**: 결제 요청에 Retry를 적용하면 **중복 결제 위험**이 있다.

| 적용 대상 | Retry 적합성 | 이유 |
|-----------|-------------|------|
| 결제 요청 (POST) | ❌ 부적합 | 멱등하지 않음, 중복 결제 위험 |
| 결제 상태 확인 (GET) | ✅ 적합 | 조회는 멱등, 부작용 없음 |
| 콜백 미수신 복구 | ✅ 적합 | 상태 확인 후 반영, 멱등 처리 가능 |

```yaml
resilience4j:
  retry:
    instances:
      pgStatusCheck:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
```

---

## 적용 아키텍처

### 레이어별 Resilience 적용 위치

```
Controller → Facade → [Resilience 경계] → PgClient → PG System
                           │
                    CircuitBreaker
                    TimeLimiter
                    Retry (GET만)
                    Fallback
```

**적용 위치**: `PgClient` 또는 `PaymentFacade` 레벨

| 위치 | 장점 | 단점 |
|------|------|------|
| PgClient (인프라) | 외부 호출에 가장 가까움, 관심사 분리 | Facade의 비즈니스 로직과 분리됨 |
| PaymentFacade (애플리케이션) | 비즈니스 컨텍스트 포함한 Fallback 가능 | 인프라 관심사가 애플리케이션에 침투 |

**추천**: Resilience 어노테이션은 Facade에, 실제 HTTP 호출은 PgClient에 분리

---

## Actuator + Prometheus 연동

Resilience4j는 자동으로 Actuator 엔드포인트와 Prometheus 메트릭을 제공한다.

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, circuitbreakers, circuitbreakerevents
  health:
    circuitbreakers:
      enabled: true
```

모니터링 가능 항목:
- 서킷 상태 (CLOSED/OPEN/HALF_OPEN)
- 실패율, 느린 호출 비율
- 호출 횟수, 실패 횟수

---

## 결정 필요 사항

| # | 항목 | 선택지 |
|---|------|--------|
| 1 | CircuitBreaker 실패 판정 | 인프라 에러만 vs 모든 예외 |
| 2 | Fallback 전략 | 즉시 에러 vs PENDING 저장 후 재시도 |
| 3 | Retry 범위 | GET(상태확인)만 vs POST(결제요청)도 포함 |
| 4 | Resilience 적용 위치 | PgClient vs PaymentFacade |
| 5 | OPEN 대기 시간 | 30s vs 60s vs 설정 가능하게 |

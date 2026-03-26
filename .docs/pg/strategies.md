# PG 외부 연동 장애 대응 전략

## 전략 간 의존 관계

```
[결제 요청]
  └─ Read Timeout 발생
       └─ PENDING 유지 (전략 4)
            └─ 재결제 차단 (전략 5)
                 └─ Callback 도달 → 즉시 해결 (전략 6)
                 └─ Callback 미도달 → Reconciliation (전략 7)
                      └─ CircuitBreaker OPEN → 다음 사이클 대기 (전략 3)

[PG 일시 장애]
  └─ Retry (전략 2)
       └─ 반복 실패 → CircuitBreaker OPEN (전략 3)
            └─ Fallback → 즉시 FAILED 반환
```

Reconciliation이 나머지 전략들이 모두 실패했을 때의 최후 안전망 역할을 한다.

---

## 1. Connect Timeout / Read Timeout 구분 처리

### 해결하는 문제
네트워크 타임아웃 발생 시 PG의 결제 처리 여부를 판단할 수 없는 불확실성.

- **Connect Timeout**: 연결 자체가 안 됨 → PG 미수신 확실 → 재시도 안전
- **Read Timeout**: 연결은 됐으나 응답 유실 → PG 수신 가능성 있음 → 재시도 시 중복 청구 위험

### 구현
```java
catch (ResourceAccessException e) {
    if (e.getCause() instanceof SocketTimeoutException ste
            && ste.getMessage().contains("connect timed out")) {
        throw e;  // Retry 허용
    }
    throw new CoreException(ErrorType.BAD_GATEWAY, ...);  // Retry 차단
}
```

### 대안

| 대안 | 설명 |
|------|------|
| 모든 타임아웃 재시도 금지 | 안전하지만 일시적 연결 실패에도 재시도 없음 |
| 멱등성 키 사용 | PG가 지원하면 모든 타임아웃에서 재시도 가능 |

### 트레이드오프
- 구분 로직이 `SocketTimeoutException` 메시지 문자열에 의존 → JDK/라이브러리 버전에 따라 메시지가 바뀌면 오작동 가능
- Connect Timeout은 재시도하므로 PG에 중복 요청이 전달될 수 있음 (미수신 확정이지만 서버 부하 증가)

---

## 2. Retry (Exponential Backoff)

### 해결하는 문제
PG 일시적 장애(5xx, Connect Timeout)로 인한 즉시 실패 처리.

### 구현
```yaml
retry:
  instances:
    pg:
      max-attempts: 3
      wait-duration: 500ms
      enable-exponential-backoff: true
      exponential-backoff-multiplier: 2
      exponential-max-wait-duration: 2s   # PG read timeout(2s) 이상은 의미 없음
      retry-exceptions:
        - HttpServerErrorException         # 5xx (PG 미처리 보장 전제)
        - ResourceAccessException          # Connect Timeout
      ignore-exceptions:
        - CoreException                    # BAD_GATEWAY(Read Timeout), BAD_REQUEST(400) 제외
```

재시도 간격: 1차 500ms → 2차 1000ms (상한 2s)

### 전제 조건
5xx는 PG가 결제를 처리하지 않았음을 보장해야 한다. 이는 PG사와의 계약으로 보장되어야 한다.

### 대안

| 대안 | 설명 |
|------|------|
| Exponential Backoff + Jitter | 재시도 간격에 무작위성을 추가해 다수 클라이언트 동시 재시도 분산 |
| Dead Letter Queue | 실패 요청을 큐에 적재, 비동기 재처리 |

### 트레이드오프
- 최대 대기: 500ms + 1000ms = 1.5초 추가 지연
- PG가 5xx를 반환했지만 실제 처리했다면 중복 위험 → PG 계약 전제

---

## 3. CircuitBreaker + Fallback

### 해결하는 문제
PG 지속 장애 시 모든 요청이 타임아웃까지 대기하며 스레드와 커넥션을 점유하는 연쇄 장애.

### 구현
```yaml
circuitbreaker:
  circuitBreakerAspectOrder: 1  # CB(outer) → Retry(inner) 순서
  instances:
    pg:
      sliding-window-size: 10
      minimum-number-of-calls: 5
      failure-rate-threshold: 50     # 50% 실패 시 OPEN
      wait-duration-in-open-state: 10s
      permitted-number-of-calls-in-half-open-state: 3
      ignore-exceptions:
        - CoreException              # 4xx, Read Timeout은 실패 카운트 제외
```

Fallback:
- `requestPayment` → FAILED 즉시 반환 (OPEN이면 PG 미호출이 확실하므로 안전)
- `getTransaction` → `CoreException(BAD_GATEWAY)` 발생

### 대안

| 대안 | 설명 |
|------|------|
| Bulkhead | 스레드 풀을 격리해 PG 장애가 다른 기능에 전파되지 않게 차단 |
| Timeout만 적용 | 단순하지만 빠른 fail-fast 없음 |

### 트레이드오프
- OPEN 상태에서 정상 요청도 거부됨
- `ignore-exceptions`에 `CoreException` 포함 → PG 장애가 4xx로 표현되면 Circuit이 열리지 않을 수 있음
- OPEN → HALF_OPEN 복구에 최대 10초 소요

---

## 4. PENDING 상태 유지 (Read Timeout 시)

### 해결하는 문제
Read Timeout 발생 시 즉시 FAILED 처리하면, 실제로 청구된 상태에서 사용자가 재결제를 시도해 중복 청구가 발생할 수 있음.

### 구현
```java
// BAD_GATEWAY(Read Timeout)이면 applyPgResponse 호출 안 함 → PENDING 유지
if (e.getErrorType() != ErrorType.BAD_GATEWAY) {
    paymentApp.applyPgResponse(orderId, null, "FAILED", ...);
}
```

### 대안

| 대안 | 설명 |
|------|------|
| 즉시 PG 재조회 후 결정 | 타임아웃 직후 getTransaction() 호출로 상태 확인 |
| FAILED 처리 + 재시도 허용 | PG 멱등성 보장이 전제여야 안전 |

### 트레이드오프
- PENDING 동안 사용자 재결제 불가 (최대 60초)
- Reconciliation에 의존하므로 Reconciliation 장애 시 장기간 PENDING 유지

---

## 5. PENDING 상태에서 재결제 차단

### 해결하는 문제
Read Timeout 후 PENDING 상태에서 사용자가 재결제를 시도할 경우 PG에 중복 요청이 전송될 수 있음.

### 구현
```java
if (payment.status() == PaymentStatus.PENDING) {
    throw new CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제입니다.");
}
```

### 대안

| 대안 | 설명 |
|------|------|
| PENDING에서도 재시도 허용 | PG 멱등성 키가 있으면 가능 |
| 새 Payment 생성 허용 | 구조가 복잡해지고 정합성 관리 어려움 |

### 트레이드오프
- 사용자 경험 저하 (재결제 불가 최대 60초)
- 이 제약이 없으면 중복 청구 위험이 직접 노출됨

---

## 6. Callback 기반 비동기 상태 동기화

### 해결하는 문제
결제 완료 후 내부 상태(TX 2) 반영 실패 시 빠른 복구.

```
PG → POST /api/v1/payments/callback → paymentApp.applyPgResponse()
```

### 대안

| 대안 | 설명 |
|------|------|
| Polling | 우리가 주기적으로 PG 상태 조회 (Reconciliation과 유사) |
| 동기 응답만 신뢰 | Callback 없이 processPayment 응답만 사용 → TX 2 실패 복구 불가 |

### 트레이드오프
- PG가 Callback 발송 실패 시 Reconciliation에 전적으로 의존
- 인증 없이 열려 있어 위조 요청에 취약 (현재 수용)
- Callback이 중복으로 오면 두 번째부터 "이미 완료된 결제" 예외 발생 → 자동 방어되지만 에러 로그 발생

---

## 7. Reconciliation 스케줄러 (최후 안전망)

### 해결하는 문제
Read Timeout + Callback 미도달이 동시에 발생해 PENDING이 장기간 유지되는 경우.

### 구현
```yaml
reconciliation:
  interval-ms: 30000            # 30초마다 실행
  pending-threshold-seconds: 30 # 30초 이상 된 PENDING만 처리
# 최대 대기 = threshold(30s) + interval(30s) = 약 60초
```

threshold 기준: Callback 최대 도달 시간(6초) + PG 처리 시간(2초) + 여유 = 30초

### 복구 전략

- `transactionKey` 있음 → 해당 트랜잭션 직접 조회
- `transactionKey` 없음 → orderId로 전체 트랜잭션 목록 조회
  - SUCCESS 있음 → SUCCESS 반영
  - 전부 FAILED → FAILED 반영
  - 전부 PENDING → 다음 사이클 대기

### 대안

| 대안 | 설명 |
|------|------|
| Outbox Pattern | DB 커밋과 이벤트 발행을 원자적으로 처리, 복구를 이벤트 기반으로 |
| 더 짧은 주기 Polling | threshold/interval을 더 줄여 빠른 복구 (PG 부하 증가) |

### 트레이드오프
- 최대 60초 지연 (사용자 관점)
- 30초마다 PG API 호출 → 처리 건수 많으면 PG 부하 증가
- Reconciliation 스케줄러 자체가 장애나면 PENDING 누적 → 모니터링 필요
- `fixedDelay` 방식이라 이전 실행이 길어지면 다음 실행이 밀림

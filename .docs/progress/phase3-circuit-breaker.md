# Phase 3: Circuit Breaker + Fallback

## 학습 목표
- Closed → Open → Half-Open 상태 전이를 설정값 기반으로 예측한다
- Sliding Window 기반 실패율 계산을 직접 수행한다
- Fallback이 "에러 메시지 변경"이 아니라 "시스템이 할 수 있는 최선"임을 이해한다

## 서킷 브레이커 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgCircuit:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10           # 최근 10개 요청 기준
        failure-rate-threshold: 50        # 실패율 50% 초과 시 Open
        wait-duration-in-open-state: 10s  # Open 유지 시간
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
```

### 설정 근거
- **sliding-window-size: 10**: 샘플이 너무 적으면(5) 잘못된 Open, 너무 크면(100) 장애 감지 느림
- **failure-rate-threshold: 50**: PG 실패율이 40%이므로, 50%를 넘으면 "평소보다 심각하게 나쁜 상태"
- **wait-duration-in-open-state: 10s**: PG 복구에 필요한 최소 시간. 너무 짧으면 Half-Open에서 바로 다시 Open
- **slow-call-duration-threshold: 2s**: Read Timeout(2초)과 동일. 에러 없이 느리기만 한 장애 감지

## 상태 전이 원리

```
CLOSED (정상)
  │
  ├─ 최근 10개 요청 중 실패율 > 50%
  │
  ▼
OPEN (차단) ──── CallNotPermittedException 즉시 발생
  │
  ├─ 10초 대기
  │
  ▼
HALF_OPEN (테스트)
  │
  ├─ 3개 요청만 통과시킴
  │   ├─ 성공률 충분 → CLOSED
  │   └─ 실패율 여전히 높음 → OPEN
  │
  ▼
CLOSED 또는 OPEN
```

## Sliding Window 실패율 수기 계산

PG 시뮬레이터 30회 요청 실측 결과:

```
요청 1~10: ❌❌✅✅❌❌✅❌✅✅ → 실패 5/10 = 50% → 경계값
요청 2~11: ❌✅✅❌❌✅❌✅✅✅ → 실패 4/10 = 40% → CLOSED 유지
요청 6~15: ❌✅❌✅✅✅✅✅❌✅ → 실패 3/10 = 30% → CLOSED 유지
```

### 관찰
- PG 실패율 40%에서 threshold 50%면, 서킷이 쉽게 열리지 않음
- 이는 **의도적인 설계**: PG가 "원래 좀 실패하는" 시스템이라면, 평소 수준을 넘어야만 서킷을 열어야 함
- threshold를 40%로 낮추면 평소에도 서킷이 자주 열려서 오히려 서비스 가용성이 떨어짐

## Fallback 전략: Level 2 (PENDING 저장)

```
외부 장애(timeout/5xx/서킷 Open) 시:
1. Payment는 TX1에서 이미 PENDING으로 저장됨
2. 사용자에게 HTTP 200 + status=PENDING 정상 응답
3. 나중에 sync API 또는 스케줄러로 복구

비재시도 장애(4xx/계약 오류) 시:
1. Payment는 PENDING으로 남음
2. HTTP 500으로 실패 전파 (운영 중 원인 추적 필요)
```

### Fallback 범위 구분
- `CallNotPermittedException` (서킷 Open) → PENDING 정상 응답
- `PaymentGatewayRetryableException` (timeout/5xx) → PENDING 정상 응답
- `PaymentGatewayException` (4xx/계약 오류) → 500 실패 전파

### Fallback에서 하지 않는 것
- 또 다른 외부 호출 ❌ (장애 전파)
- 무거운 DB 쿼리 ❌ (커넥션 풀 압박)
- 사용자에게 거짓 성공 응답 ❌ (신뢰 훼손)
- 4xx/계약 오류를 정상 응답으로 숨기기 ❌ (원인 추적 불가)

### Fallback이 동작하는 이유
현재 구조에서 PG 호출은 TX1(PENDING 저장) 이후에 발생하므로, PG 호출이 실패해도 Payment는 이미 DB에 저장되어 있다. 이것이 **트랜잭션 경계 분리의 또 다른 이점**: Fallback 시 별도 저장 로직 없이 자연스럽게 PENDING 상태가 유지된다.

## 서킷 브레이커 이벤트 로그

`CircuitBreakerEventConfig`에서 다음 이벤트를 로그로 기록:
- **상태 전이**: CLOSED → OPEN → HALF_OPEN → CLOSED
- **실패율 초과**: 현재 실패율 출력
- **느린 호출 비율 초과**: 현재 비율 출력
- **호출 차단**: 서킷 Open 시 요청 거부

Actuator 엔드포인트: `/actuator/circuitbreakers`로 실시간 모니터링 가능

## PG 시뮬레이터 실측 결과 (30회)

| 항목 | 값 |
|---|---|
| 성공률 | 63% (19/30) |
| 실패율 | 36% (11/30) |
| 서킷 Open 발생 | 관찰되지 않음 (40% 실패율 < 50% threshold) |

### 인프라 장애 관찰
- PG 시뮬레이터의 MySQL이 죽으면 HikariCP 풀이 망가짐
- 앱 재시작(docker compose restart) 필요
- **실무에서도 HikariCP 풀 장애 → 앱 재시작은 흔한 패턴**
- 서킷 브레이커는 이런 "완전 죽은" 상태에서 100% 실패율 → 즉시 Open → 우리 시스템 보호

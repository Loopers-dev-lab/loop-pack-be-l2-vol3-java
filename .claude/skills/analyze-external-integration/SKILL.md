---
name: analyze-external-integration
description:
  외부 시스템과 연동되는 코드를 탐색하고, 상태 불일치·트랜잭션 경계·장애 시나리오 관점에서 분석한다.

  특히 다음을 중점적으로 점검한다.
  - 우리 시스템과 외부 시스템 간 상태가 불일치할 수 있는 시나리오는 없는지
  - 외부 호출이 트랜잭션 내부에 있어 DB 커넥션을 장시간 점유하지는 않는지
  - 타임아웃, 콜백 유실, 중복 호출 등 장애 시나리오에 대한 대비가 되어있는지
  - Resilience 패턴(Retry, CircuitBreaker, Fallback)이 올바르게 적용되어 있는지

  단순한 정답 제시가 아니라, 현재 구조의 의도와 trade-off를 드러내고 개선 가능 지점을 선택적으로 판단할 수 있도록 돕는다.
---

### Analysis Scope
이 스킬은 아래 대상에 대해 분석한다.
- 외부 시스템을 호출하는 클래스 (FeignClient, RestTemplate, WebClient 등)
- 외부 호출을 감싸는 Gateway/Adapter 클래스 (Resilience 어노테이션 적용 대상)
- 외부 연동을 조율하는 Facade/Application 레이어 코드
- 외부 시스템으로부터 수신하는 콜백/웹훅 처리 코드
- 하나의 유즈케이스(요청 흐름) 단위

> 컨트롤러 → Facade → Gateway → 외부 클라이언트 전체 흐름을 기준으로 분석하며, 특정 메서드만 떼어내어 판단하지 않는다.

### Project Context
본 프로젝트의 아키텍처 특성을 전제로 분석한다.

#### 레이어 구조
```
Controller → Facade (@Transactional) → Gateway (@Retry, @CircuitBreaker) → FeignClient (외부 HTTP)
                                     → Domain Service → Repository
```
- **Facade**: 트랜잭션 시작점. 도메인 서비스와 외부 Gateway를 조합하여 유즈케이스를 구현한다.
- **Gateway**: 외부 호출을 감싸는 Resilience 레이어. @Retry, @CircuitBreaker, fallback을 담당한다.
- **FeignClient**: 외부 시스템과의 HTTP 통신 인터페이스.

#### 외부 연동 흐름 예시
```
PaymentFacade.requestPayment()
  ├─ MemberService.getMyInfo()              // [조회] 인증
  ├─ OrderService.getById()                 // [조회] 주문 확인
  ├─ PaymentService.create()                // [쓰기] 결제 생성 (PENDING)
  ├─ PgPaymentGateway.requestPayment()      // [외부] PG 결제 요청
  │     ├─ @CircuitBreaker → @Retry → PgClient.requestPayment()
  │     └─ 실패 시 fallback 응답 반환
  └─ payment.markTimedOut() 또는 assignTransactionId()  // [쓰기] 상태 반영
```

### Analysis Checklist

#### 1. 상태 불일치 시나리오 분석
우리 시스템과 외부 시스템 간 상태가 다를 수 있는 시나리오를 식별한다.

- [ ] 외부 요청은 성공했으나 우리 DB 저장이 실패하는 경우
  - 예: PG에서 transactionId를 발급했으나 DB commit 실패 → PG는 결제 진행 중, 우리는 기록 없음
- [ ] 외부 요청이 타임아웃됐으나 외부에서는 처리가 완료된 경우
  - 예: PG가 실제로 결제를 승인했으나 응답이 늦어 타임아웃 → 우리는 TIMED_OUT, PG는 SUCCESS
- [ ] 콜백이 유실되어 최종 상태가 반영되지 않는 경우
  - 예: PG가 콜백을 보냈으나 네트워크 오류로 수신 실패 → 영원히 PENDING
- [ ] 콜백이 중복 수신되는 경우
  - 예: PG가 같은 콜백을 2번 발송 → 이중 처리 위험
- [ ] 외부 시스템 장애 복구 후 밀린 콜백이 한꺼번에 도착하는 경우

**출력 형식**
```
- 식별된 불일치 시나리오:
  1. [시나리오명]
     - 발생 조건: (어떤 상황에서 발생하는지)
     - 우리 상태: (우리 시스템의 상태)
     - 외부 상태: (외부 시스템의 상태)
     - 현재 대비: (코드에서 이를 처리하고 있는지)
     - 복구 방법: (상태를 일치시키는 방법)
```

#### 2. 트랜잭션 경계와 외부 호출 분석
외부 호출이 트랜잭션과 어떻게 상호작용하는지 점검한다.

- [ ] 외부 호출이 @Transactional 내부에 있는가?
  - DB 커넥션을 외부 응답 대기 시간만큼 점유하게 됨
  - 외부 시스템 지연 → DB 커넥션 풀 고갈 → 전체 시스템 마비 위험
- [ ] 외부 호출 실패 시 이전 DB 작업이 롤백되는가? 의도된 동작인가?
  - 예: Payment PENDING 저장 후 PG 호출 실패 → Payment 기록도 롤백?
  - 의도: PG 실패해도 TIMED_OUT 상태로 저장하고 나중에 복구해야 할 수 있음
- [ ] 외부 호출 후 상태 변경이 같은 트랜잭션에서 이루어지는가?
  - commit 전에 예외 발생하면 외부는 처리됐는데 우리 DB에는 미반영
- [ ] 트랜잭션 타임아웃과 외부 호출 타임아웃의 관계
  - 외부 타임아웃(3초) + Retry(3회) = 최대 9초 → 트랜잭션 타임아웃보다 긴가?

**출력 형식**
```
- 현재 트랜잭션-외부호출 구조:
  XxxFacade.method() [@Transactional]
    ├─ [DB 쓰기] Payment 생성
    ├─ [외부 호출] PG 결제 요청 (최대 9초 소요 가능)
    └─ [DB 쓰기] 상태 업데이트

- DB 커넥션 점유 시간: 최소 ~ms, 최대 ~초
- 위험도: (높음/중간/낮음)
- trade-off: (현재 구조를 유지하는 이유 vs 분리했을 때의 이점)
```

#### 3. 장애 시나리오 대비 점검
외부 시스템의 다양한 장애 유형에 대한 대비를 점검한다.

**외부 시스템 장애 유형**
- [ ] 응답 지연 (Slow Response)
  - 타임아웃 설정이 되어있는가?
  - 타임아웃 값은 적절한가? (너무 짧으면 정상 요청도 실패, 너무 길면 스레드 점유)
- [ ] 완전 장애 (Connection Refused / DNS Failure)
  - 즉시 실패하는가, 아니면 타임아웃까지 대기하는가?
- [ ] 간헐적 장애 (Intermittent Failure)
  - Retry가 설정되어 있는가?
  - 재시도 간격(backoff)은 적절한가?
  - 최대 재시도 횟수는 적절한가?
- [ ] 지속적 장애 (Prolonged Outage)
  - CircuitBreaker가 설정되어 있는가?
  - OPEN 상태에서 fallback이 적절한 응답을 반환하는가?
  - HALF_OPEN 전이 시간은 적절한가?

**콜백/비동기 처리 장애**
- [ ] 콜백 수신 실패 시 복구 수단이 있는가? (수동 API, 스케줄러)
- [ ] 콜백 처리 중 예외 발생 시 재처리가 가능한가?
- [ ] 콜백의 멱등성이 보장되는가? (같은 콜백 2번 와도 안전한가?)

**출력 형식**
```
- 장애 시나리오 점검표:
  | 장애 유형 | 대비 여부 | 구현 방식 | 미비 사항 |
  |-----------|-----------|-----------|-----------|
  | 응답 지연 | ✅ | Feign connectTimeout 1s, readTimeout 3s | - |
  | 간헐적 장애 | ✅ | @Retry max-attempts 3 | - |
  | 지속적 장애 | ✅ | @CircuitBreaker 50% threshold | - |
  | 콜백 유실 | ✅ | syncPaymentStatus API | 자동 복구(스케줄러) 없음 |
```

#### 4. Resilience 패턴 적용 검증
Resilience4j 설정과 적용이 올바른지 점검한다.

**AOP 프록시 동작**
- [ ] @Retry, @CircuitBreaker가 붙은 메서드가 외부에서 호출되는가? (self-invocation 문제)
- [ ] 별도 클래스로 분리되어 Spring 프록시를 타는가?

**어노테이션 조합**
- [ ] CircuitBreaker와 Retry의 aspect-order가 올바른가?
  - CircuitBreaker(바깥, order 낮음) → Retry(안쪽, order 높음)
  - 반대면: CB OPEN인데 Retry가 재시도 시도 → 무의미한 호출
- [ ] fallbackMethod가 올바른 위치에 있는가?
  - @Retry에 fallback이 있으면 CB가 실패를 감지 못함
  - fallback은 가장 바깥(@CircuitBreaker)에만 있어야 함
- [ ] fallbackMethod 시그니처가 올바른가?
  - 원본과 같은 파라미터 + 마지막에 Throwable
  - 반환 타입 일치

**설정값 적절성**
- [ ] retry-exceptions / ignore-exceptions 설정이 적절한가?
  - 비즈니스 예외(CoreException)는 재시도하면 안 됨
  - 네트워크/외부 예외만 재시도해야 함
- [ ] CircuitBreaker의 minimum-number-of-calls가 설정되어 있는가?
  - 없으면 서비스 시작 직후 1건 실패로 서킷 OPEN 가능
- [ ] slow-call-duration-threshold가 설정되어 있는가?
  - 외부 시스템이 "죽지는 않았지만 느려진 경우"가 더 위험

**출력 형식**
```
- Resilience 적용 구조:
  요청 → CircuitBreaker(order=1, fallback) → Retry(order=2) → FeignClient

- 설정 검증:
  | 항목 | 설정값 | 적절성 | 비고 |
  |------|--------|--------|------|
  | retry max-attempts | 3 | ✅ | - |
  | CB failure-rate-threshold | 50% | ✅ | - |
  | CB minimum-number-of-calls | 5 | ✅ | 조기 OPEN 방지 |
```

#### 5. Improvement Proposal (선택적 제안)
개선안은 강제하지 않고 선택지로 제시한다. 현재 구조의 의도를 존중하되, trade-off를 명확히 한다.

**가능한 개선 방향**
- 트랜잭션과 외부 호출 분리
  - 외부 호출을 트랜잭션 밖으로 이동하여 DB 커넥션 점유 최소화
  - trade-off: 코드 복잡도 증가, 부분 실패 시 보상 트랜잭션 필요
- 콜백 유실 자동 복구
  - 스케줄러로 PENDING/TIMED_OUT 상태 결제 주기적 확인
  - trade-off: PG에 추가 부하, 스케줄러 관리 필요
- 비동기 외부 호출
  - @Async 또는 이벤트 기반으로 외부 호출을 비동기 처리
  - trade-off: 에러 핸들링 복잡, 즉시 응답에 외부 결과 포함 불가
- Bulkhead 패턴 추가
  - 외부 호출용 스레드 풀 분리하여 전체 시스템 보호
  - trade-off: 설정 복잡도, 리소스 효율

**개선안 작성 형식**
```
[개선안 N] 제목
- 현재: (현재 동작 설명)
- 제안: (개선 방향)
- 이유: (왜 개선이 필요한지)
- trade-off: (개선 시 발생할 수 있는 부작용 또는 고려사항)
- 판단: 개발자가 결정할 사항 명시
```

### 분석 시 주의사항
- 단순 패턴 매칭으로 문제를 지적하지 않는다. 흐름 전체를 보고 판단한다.
- "이렇게 하면 안 된다"가 아니라 "이렇게 하면 이런 trade-off가 있다"로 제시한다.
- 현재 규모와 요구사항에서 과도한 최적화를 권하지 않는다.
- 외부 시스템의 특성(응답 지연, 성공률, 비동기 여부)을 고려하여 분석한다.
- 개선이 필요 없는 경우 "현재 구조가 적절하다"고 명시한다.

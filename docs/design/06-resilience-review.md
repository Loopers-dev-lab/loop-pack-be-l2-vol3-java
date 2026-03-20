# Resilience 설계 리뷰 — 시니어 아키텍트 관점

---

## 0. MSA 이커머스 점검 프레임워크

> 대규모 트래픽이 발생하는 MSA 이커머스 시스템을 가정하고,
> 외부 시스템 연동 설계를 점검하기 위한 기준이다.
> 설계/구현 전후에 이 프레임워크를 대입하여 빈틈을 식별한다.

### 점검 기준표

| # | 점검 영역 | 핵심 질문 | 위험 수준 |
|---|----------|----------|----------|
| **C1** | **장애 격리** | 외부 시스템 장애가 내부 서비스로 전파되는가? 결제 장애가 상품 조회에 영향을 주는가? | 치명적 |
| **C2** | **리소스 보호** | 외부 호출 지연 시 스레드/커넥션/메모리가 고갈될 수 있는가? | 치명적 |
| **C3** | **데이터 정합성** | 내부 상태와 외부 상태가 어긋날 수 있는 지점은? 어긋났을 때 감지하고 복구할 수 있는가? | 치명적 |
| **C4** | **멱등성** | 동일 요청이 2번 실행되면 부작용이 발생하는가? (중복 결제, 중복 차감 등) | 치명적 |
| **C5** | **트랜잭션 경계** | 외부 호출이 DB 트랜잭션 안에 있는가? 커넥션 점유 시간은 적절한가? | 높음 |
| **C6** | **타임아웃 체인** | 상위 서비스 타임아웃 > 하위 서비스 타임아웃을 만족하는가? 타임아웃이 누락된 호출이 있는가? | 높음 |
| **C7** | **동시성** | 같은 자원에 대한 동시 요청이 경합하는 지점은? Race Condition이 존재하는가? | 높음 |
| **C8** | **복구 가능성** | 장애 발생 후 자동 복구 경로가 있는가? 수동 개입 없이 정상 상태로 돌아올 수 있는가? | 높음 |
| **C9** | **관측 가능성** | 장애 발생을 감지할 수 있는가? Circuit Breaker 상태 변화, 실패율, 복구 대상 건수를 알 수 있는가? | 중간 |
| **C10** | **Graceful Degradation** | 외부 시스템 장애 시 사용자에게 어떤 경험을 제공하는가? 거짓 정보를 전달하지 않는가? | 중간 |
| **C11** | **배압(Backpressure)** | Circuit Breaker가 닫힐 때, 대기 중이던 요청이 한꺼번에 몰리는가? (Thundering Herd) | 중간 |
| **C12** | **SLA 정합성** | 우리 서비스의 응답시간 SLA가 외부 시스템 지연 + Retry 시간을 포함하여 유지되는가? | 중간 |

### 점검 프로세스

```
1. 설계 문서(05)의 각 흐름을 C1~C12 기준으로 대입
2. 위험 수준이 "치명적"인 항목 우선 점검
3. 빈틈 발견 시 → 선택지 도출 → 트레이드오프 분석 → 결정 + 근거 기록
4. 구현 후 다시 점검 (특히 C3, C4, C7은 코드 레벨에서 재확인)
```

---

## 1. 점검 결과: 우리 설계 대입

### C1. 장애 격리 — 통과

| 점검 | 결과 |
|------|------|
| PG 장애 → 상품 조회 영향? | **없음**. PG 호출은 결제 API에서만 발생 |
| PG 장애 → 주문 생성 영향? | **없음**. 주문 생성과 결제는 별도 API |
| CircuitBreaker 적용? | **적용됨**. PG 전면 장애 시 호출 차단 → 즉시 Fallback |

**보완 필요 없음.**

### C2. 리소스 보호 — 통과

| 점검 | 결과 |
|------|------|
| 스레드 고갈 | Timeout 1초 + 최대 Retry 4.5초로 제한. 무한 대기 불가 |
| DB 커넥션 고갈 | PG 호출은 트랜잭션 밖. 커넥션 점유 ~수십ms |
| 커넥션 풀 계산 | 초당 100건 × 0.05초 = 5 커넥션·초 (TX 안이면 450 커넥션·초) |

**보완 필요 없음.** 트랜잭션 분리가 핵심 방어.

### C3. 데이터 정합성 — 보완 필요

| 점검 | 결과 |
|------|------|
| 내부-외부 상태 불일치 가능 지점 | **타임아웃 시** — 내부 UNKNOWN, PG는 PENDING/SUCCESS 가능 |
| 감지 가능한가? | **가능**. 콜백 + 배치 폴링 |
| 복구 가능한가? | **가능**. PG 상태 확인 API로 확정 |
| REQUESTED 상태 방치 가능? | **가능**. TX-1 커밋 후 PG 호출 전 서버 크래시 시 |

**보완**: 배치 복구 대상에 REQUESTED 상태 포함 필수.

```
기존: 배치 대상 = PENDING(N분 경과) + UNKNOWN
수정: 배치 대상 = REQUESTED(N분 경과) + PENDING(N분 경과) + UNKNOWN
```

REQUESTED가 N분 이상 지속 → PG 조회 → 404이면 FAILED 처리 (PG에 도달하지 못한 것)

### C4. 멱등성 — 보완 완료 (치명적이었음)

| 점검 | 결과 |
|------|------|
| 결제 요청 중복 실행 | **PG가 멱등하지 않음** → 재시도 시 중복 결제 위험 |
| 해결 | 재시도 전 PG 상태 확인 (수동 Retry 루프) |
| 콜백 중복 수신 | 이미 최종 상태이면 무시 (멱등) |
| 동시 결제 요청 | Payment 테이블 UNIQUE(order_id) |

**06 리뷰에서 식별되어 반영 완료.**

### C5. 트랜잭션 경계 — 통과

| 점검 | 결과 |
|------|------|
| 외부 호출 위치 | 트랜잭션 밖 |
| 커넥션 점유 시간 | ~수십ms (Payment 저장/업데이트만) |
| 콜백 처리 시 | Payment + Order 같은 TX (모노리스, 같은 DB) |

**현재 구조에서 적절.** MSA 전환 시 콜백 처리의 TX 분리 + 이벤트 기반 전환 고려.

### C6. 타임아웃 체인 — 점검 필요

MSA에서는 호출 체인의 타임아웃이 계층적으로 맞아야 한다:

```
[사용자] ---(응답 대기 10초)--→ [API Gateway] ---(5초)--→ [Payment Service] ---(1초)--→ [PG]
```

| 점검 | 결과 |
|------|------|
| 사용자 → Commerce API | 별도 설정 없음 (Tomcat 기본) |
| Commerce API → PG | Timeout 1초 × 최대 3회 = 4.5초 |
| 상위 타임아웃 > 하위 타임아웃? | Tomcat 기본 타임아웃(60초) > 4.5초 → **충족** |

**현재 구조에서 문제 없음.** 다만 향후 API Gateway 도입 시 gateway timeout > 4.5초 보장 필요.

### C7. 동시성 — 보완 완료

| 점검 | 결과 |
|------|------|
| 같은 주문에 동시 결제 | Payment UNIQUE(order_id)로 방지 |
| 콜백과 배치 동시 실행 | 같은 Payment를 동시에 업데이트할 수 있음 |

**보완 필요**: 콜백 처리와 배치 복구가 동시에 같은 Payment를 건드릴 수 있다.

| 선택지 | 동작 | 장점 | 단점 |
|--------|------|------|------|
| A. 비관적 락 | `SELECT ... FOR UPDATE` | 확실한 동시성 제어 | 락 경합, 배치 지연 |
| **B. 상태 기반 조건부 UPDATE** | `UPDATE ... WHERE status = 'PENDING'` | **락 없이 원자적 전이, 단순** | affected rows 확인 필요 |
| C. 낙관적 락 (version) | `@Version` 필드 | JPA 친화적 | 충돌 시 재시도 로직 필요 |

**결정: B. 조건부 UPDATE**

```sql
UPDATE payment SET status = 'PAID' WHERE id = ? AND status IN ('PENDING', 'UNKNOWN')
-- affected rows = 0이면 이미 다른 경로(콜백/배치)에서 처리 완료 → 무시
```

근거:
- 콜백과 배치가 동시에 같은 건을 처리해도, 먼저 UPDATE 성공한 쪽이 확정
- 나중에 UPDATE한 쪽은 affected rows = 0 → 추가 처리 없이 종료
- 락이 없어 성능 영향 최소화
- 기존 쿠폰 사용에서도 같은 패턴 적용 중 (조건부 UPDATE)

### C8. 복구 가능성 — 보완 완료

| 점검 | 결과 |
|------|------|
| UNKNOWN 복구 | 콜백 + 배치 이중 안전망 |
| REQUESTED 복구 | **배치 대상에 추가 필요** (C3에서 식별) |
| PENDING 장기 체류 복구 | 배치가 1분 후 PG 확인 |
| 수동 복구 | 관리자/사용자 API 제공 |

**자동 복구 경로 존재 확인.** REQUESTED 추가 반영 후 완전.

### C9. 관측 가능성 — 보완 필요

| 점검 | 결과 |
|------|------|
| CB 상태 변화 감지 | 설정만으로는 로그 없음 |
| 실패율 모니터링 | Resilience4j Actuator 연동 필요 |
| 복구 대상 건수 추적 | UNKNOWN/PENDING 건수 쿼리 필요 |

**보완**: 구현 단계에서 아래 추가

```yaml
# Actuator로 CB 상태 노출
management:
  endpoints:
    web:
      exposure:
        include: health,circuitbreakers
  health:
    circuitbreakers:
      enabled: true
```

**우선순위: 낮음** — 기능 구현 후 부가적으로 추가. 과제 스코프에서는 로깅으로 대체 가능.

### C10. Graceful Degradation — 통과

| 점검 | 결과 |
|------|------|
| Fallback 메시지 | "결제 확인 중입니다. 잠시 후 확인해주세요" |
| 거짓 정보 전달? | **없음**. 성공/실패를 확인 못 한 상태를 그대로 전달 |
| CB Open 시 경험 | 즉시 Fallback → 사용자 대기 시간 최소화 |

**보완 필요 없음.**

### C11. 배압(Thundering Herd) — 현재 위험 낮음

| 점검 | 결과 |
|------|------|
| CB Open → Close 전환 시 | Half-Open에서 2건만 허용 → 점진적 복구 |
| 대기 중 요청 폭주 | CB Open 중에는 Fallback 처리 → 대기열 없음 |

**보완 필요 없음.** Resilience4j의 Half-Open 메커니즘이 자연스럽게 처리.

### C12. SLA 정합성 — 통과

| 점검 | 결과 |
|------|------|
| 결제 API 최대 응답 시간 | 4.5초 (Retry 전부 실패 시) |
| 결제 UX 허용 범위 | 5~10초 |
| CB Open 시 응답 시간 | ~즉시 (Fallback) |

**보완 필요 없음.**

---

## 2. 점검 결과 요약

### 점검 통과

| # | 영역 | 상태 |
|---|------|------|
| C1 | 장애 격리 | **통과** |
| C2 | 리소스 보호 | **통과** |
| C5 | 트랜잭션 경계 | **통과** |
| C6 | 타임아웃 체인 | **통과** |
| C10 | Graceful Degradation | **통과** |
| C11 | 배압 | **통과** |
| C12 | SLA 정합성 | **통과** |

### 보완 완료 (06 리뷰에서 식별)

| # | 영역 | 보완 내용 |
|---|------|----------|
| C4 | 멱등성 | 수동 Retry + PG 상태 확인, UNIQUE(order_id) |

### 보완 필요 (이번 점검에서 추가 식별)

| # | 영역 | 보완 내용 | 우선순위 |
|---|------|----------|---------|
| C3 | 데이터 정합성 | 배치 복구 대상에 REQUESTED 상태 추가 | **높음** |
| C7 | 동시성 | 콜백/배치 동시 실행 방지 → 조건부 UPDATE | **높음** |
| C9 | 관측 가능성 | CB 상태 모니터링 (Actuator 또는 로깅) | 낮음 |

---

## 3. 기존 리뷰 내용

### 3.1 Resilience 패턴 적용 원칙

대규모 트래픽 이커머스에서 외부 시스템(PG, 배송, 알림 등) 호출의 기본 원칙은
**"외부 장애가 내부로 전파되지 않는 것"**이다. 이를 위해 4단계 방어선을 구축한다.

```
[1차 방어] Timeout — 개별 요청의 최대 대기 시간 제한
[2차 방어] Retry — 일시적 실패에 대한 자동 재시도
[3차 방어] CircuitBreaker — 반복 실패 시 호출 자체를 차단
[최후 방어] Fallback — 모든 방어가 뚫렸을 때 사용자에게 안전한 응답
```

### 3.2 Timeout 설정 기준

실무에서 타임아웃은 **"P99 응답시간의 2~3배"**로 잡는다.

```
PG 정상 응답: 100~500ms (시뮬레이터 기준)
P99 추정: ~500ms
타임아웃 기준: 500ms × 2 = 1,000ms (1초)
```

connectTimeout과 readTimeout을 구분한다:
- **connectTimeout**: TCP 연결 수립까지. PG가 살아있는지 확인. (500ms)
- **readTimeout**: 연결 후 응답 대기 시간. 실제 처리 시간 반영. (1초)

### 3.3 Retry 설정 기준

재시도는 **멱등하지 않은 요청에 대해 매우 신중**해야 한다.

- GET (조회): 자유롭게 재시도 가능
- POST (결제 요청): **중복 생성 위험** → 재시도 전 반드시 상태 확인 필요

### 3.4 CircuitBreaker 설정 기준

서비스별로 별도 인스턴스를 두고, **비즈니스 임팩트에 따라 임계치를 다르게** 설정한다.

- 결제(PG): 보수적 (임계치 높게, 빨리 차단하지 않음) — 돈이 걸려있으므로 최대한 시도
- 알림(카카오톡): 공격적 (임계치 낮게, 빨리 차단) — 실패해도 치명적이지 않음

### 3.5 Fallback 처리

결제 Fallback의 핵심은 **"사용자에게 거짓말하지 않는 것"**이다.

```
X "결제가 완료되었습니다" (확인 안 됐는데)
X "결제가 실패했습니다" (PG에서는 성공했을 수 있는데)
O "결제 확인 중입니다. 잠시 후 결제 내역에서 확인해주세요"
```

---

## 4. 비동기 결제 상태 관리

### 4.1 내부 결제 상태 검증

5단계 상태(REQUESTED → PENDING → PAID/FAILED/UNKNOWN)는 적절하다.

UNKNOWN 상태의 세분화는 필요한가?

| 선택지 | 설명 | 판단 |
|--------|------|------|
| A. UNKNOWN 하나로 통합 | 원인 불문하고 "모르겠다" | **현재는 충분** |
| B. TIMEOUT / CALLBACK_MISSING 분리 | 원인별 복구 전략 차별화 | 과제 범위에서 과도함 |

**결정: A. UNKNOWN 하나로 충분.** 복구 방법이 동일 (PG 상태 확인 API 호출).

### 4.2 유령 결제 처리

"타임아웃으로 실패 처리했는데, PG에서는 결제가 성공한 경우"

핵심은 **"감지할 수 있는가"**와 **"복구할 수 있는가"**이다.

| 감지 방법 | 복구 방법 |
|----------|----------|
| 콜백으로 감지 (PG가 성공 콜백을 보냄) | SUCCESS → PAID 전이 |
| 배치로 감지 (PG 상태 확인 API 조회) | FAILED → FAILED 전이 + 재고 복원 |

UNKNOWN 상태가 이 역할을 수행한다.

---

## 5. 멱등성 — 핵심 리스크

### 5.1 결제 요청 중복 방지

PG 시뮬레이터는 같은 orderId로 요청해도 **별도 결제건을 생성**한다.
PG 자체가 멱등하지 않으므로, 우리 측에서 보장해야 한다.

**결정: 재시도 전 PG 상태 확인 (수동 Retry 루프)**

```
1차 시도 → 타임아웃
재시도 전: GET /api/v1/payments?orderId={orderId} 로 PG 조회
  |-- PG에 기록 있음 → 재시도 안 함, 해당 transactionKey로 추적
  +-- PG에 기록 없음 (404) → 안전하게 재시도
```

### 5.2 동일 주문 동시 결제 방지

Payment 테이블에 `UNIQUE(order_id)` 제약. 같은 주문에 대해 동시 결제 요청이 들어오면 두 번째 요청은 DB 유니크 위반으로 거부.

### 5.3 콜백/배치 동시 실행 방지

조건부 UPDATE로 해결:
```sql
UPDATE payment SET status = 'PAID' WHERE id = ? AND status IN ('PENDING', 'UNKNOWN')
```
affected rows = 0이면 이미 다른 경로에서 처리 완료.

---

## 6. 트랜잭션 경계

### 6.1 PG 호출은 트랜잭션 밖

| 관점 | TX 안에서 외부 호출 | TX 밖에서 외부 호출 |
|------|-------------------|-------------------|
| DB 커넥션 점유 | PG 응답까지 점유 (최대 4.5초) | Payment 저장 시간만 (~수십ms) |
| 초당 100건 시 | 커넥션 100개 × 4.5초 = **450 커넥션·초** | 100개 × 0.05초 = **5 커넥션·초** |
| 장애 전파 | PG 지연 → DB 커넥션 고갈 → 전체 마비 | PG 지연 → 결제만 영향 |

### 6.2 콜백 수신 시 트랜잭션

Payment + Order를 같은 트랜잭션에서 업데이트.

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **같은 TX** | **원자성 보장, 단순** | 두 도메인이 결합 |
| 별도 TX + 이벤트 | 느슨한 결합 | 현재 불필요한 복잡성 |

**결정: 같은 TX.** 모노리스 + 같은 DB. MSA 전환 시 이벤트 기반으로 전환.

### 6.3 PENDING 중 주문 취소

| 선택지 | 설명 | 결정 |
|--------|------|------|
| A. 취소 불가 | 결제 결과 대기 후 처리 | **선택** |
| B. 취소 허용 + 환불 | UX 우선 | PG 환불 API 없어 불가 |

---

## 7. PG 타이밍 기반 전략 점검

### 7.1 PG 시뮬레이터 타이밍

```
[요청] Thread.sleep(100~500ms) → 40%: 500 에러 / 60%: PENDING 응답
[비동기] Thread.sleep(1~5초) → 70%: SUCCESS / 30%: FAILED
[콜백] RestTemplate POST → 실패 시 재시도 없음
```

### 7.2 Timeout 1초 검증

| 항목 | 값 |
|------|-----|
| PG 정상 응답 | 110~550ms |
| 우리 readTimeout | 1,000ms |
| 여유 | 450~890ms |
| 정상 요청 타임아웃 확률 | **거의 0%** |

**적절.**

### 7.3 Retry 타이밍 검증

| 시나리오 | 소요 시간 | UX |
|----------|----------|-----|
| 1차 성공 | 0.1~0.5초 | 즉시 |
| 2차 성공 | 0.7~1.5초 | 허용 |
| 3차 성공 | 1.5~3초 | 체감되지만 결제로 허용 |
| 전체 실패 → Fallback | 4.5초 | 한계 (5초 이내 OK) |

**적절.**

### 7.4 CircuitBreaker 실패율 재검증

Retry가 CB 안쪽이므로, CB가 보는 실패율은 Retry 후 최종 결과이다.

```
PG 기본 실패율: 40%
3회 연속 실패 확률: 0.4³ = 6.4%
CB가 보는 최종 실패율: ~6.4%
CB 임계치: 50%

→ 정상 운영에서 CB가 열릴 가능성: 거의 없음
→ CB가 열리려면: PG 거의 전면 장애
```

**의도대로 동작.** CB는 PG 전면 장애 시에만 작동.

### 7.5 Fallback 후 복구까지의 시간 간극

| 상황 | 콜백 가능성 | 복구 경로 |
|------|-----------|----------|
| 3회 모두 PG 500 | 안 옴 | 배치 → PG 404 → FAILED |
| 1차 타임아웃 (PG 도달) + 2,3차 실패 | **올 수 있음** | 콜백 또는 배치 |
| PG 완전 다운 (CB Open) | 안 옴 | 배치 → PG 404 → FAILED |

**UNKNOWN 상태의 결제건은 콜백 + 배치 이중 안전망으로 반드시 복구된다.**

---

## 8. 설계 보완 사항 최종 요약

### 반영 완료

| # | 보완 사항 | 근거 | 출처 |
|---|----------|------|------|
| 1 | Retry 전 PG 상태 확인 (수동 Retry) | 중복 결제 방지 (C4) | 06 리뷰 |
| 2 | Payment UNIQUE(order_id) | 동시 결제 방지 (C7) | 06 리뷰 |

### 추가 반영 필요 (→ 05 설계 문서에 반영)

| # | 보완 사항 | 근거 | 우선순위 |
|---|----------|------|---------|
| 3 | 배치 복구 대상에 REQUESTED 추가 | 서버 크래시 시 PG 호출 전 방치 방지 (C3) | **높음** |
| 4 | 콜백/배치 동시성 → 조건부 UPDATE | 상태 전이 경합 방지 (C7) | **높음** |
| 5 | connectTimeout 500ms로 분리 | PG 연결 불가 시 빠른 감지 (C6) | 중간 |
| 6 | CB 상태 로깅/모니터링 | 장애 감지 및 운영 (C9) | 낮음 |
| 7 | Transactional Outbox 패턴 적용 | PG 호출 신뢰성 보장 (C3, C8) | **높음** |
| 8 | Multi-PG Fallback (Toss sandbox) | PG 전면 장애 시 대체 경로 확보 (C1, C10) | **높음** |
| 9 | Fallback 지점 전수 점검 및 구체화 | 모든 실패 경로에 대한 대응 보장 (C10) | **높음** |
| 10 | Polling Hybrid (Delayed Task) | 콜백 미수신 시 10초 내 능동적 복구 (C8, C12) | **높음** |
| 11 | Callback Inbox (DLQ) | 콜백 데이터 유실 방지 + 재처리 (C3, C8) | **높음** |
| 12 | Local WAL | DB 장애 시 PG 응답 보존 (C3) | 중간 |
| 13 | 카드사별 장애 모니터링 | 카드사 장애 시 사전 안내 (C10) | 설계만 |

---

## 9. Transactional Outbox 패턴 적용 분석

### 9.1 현재 설계의 취약점

```
[TX-1] Payment(REQUESTED) 저장 → commit
[PG 호출] CircuitBreaker → Retry → PG 요청 (트랜잭션 없음)
[TX-2] Payment 상태 업데이트 → commit
```

TX-1 커밋과 PG 호출 사이에 **서버 크래시**가 발생하면:
- Payment는 REQUESTED 상태로 DB에 존재
- PG 호출은 아예 발생하지 않음
- 배치 복구(C3 보완)가 이 건을 감지하여 처리할 수 있지만, **배치 주기(1분)만큼 지연**

### 9.2 Outbox 패턴 적용 시 구조

```
[TX-1] Payment(REQUESTED) + PaymentOutbox(PENDING) 저장 → commit
[Outbox Poller] PaymentOutbox(PENDING) 조회 → PG 호출 → PaymentOutbox(PROCESSED)
[TX-2] Payment 상태 업데이트 → commit
```

핵심: **Payment 생성과 "PG를 호출해야 한다"는 의도를 같은 트랜잭션으로 원자적 저장**.
Outbox 폴러가 미처리 건을 지속적으로 처리하므로, 서버 크래시에도 PG 호출이 누락되지 않는다.

### 9.3 Outbox 테이블 설계 (안)

```sql
CREATE TABLE payment_outbox (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  BIGINT NOT NULL,
    order_id    VARCHAR(50) NOT NULL,
    event_type  VARCHAR(30) NOT NULL,   -- 'PAYMENT_REQUEST'
    payload     TEXT NOT NULL,           -- PG 요청 Body (JSON)
    status      VARCHAR(20) NOT NULL,   -- 'PENDING' / 'PROCESSED' / 'FAILED'
    created_at  DATETIME NOT NULL,
    processed_at DATETIME,
    retry_count INT DEFAULT 0
);
```

### 9.4 Outbox vs 배치 복구 비교

| 기준 | 배치 복구 (현재) | Outbox 패턴 |
|------|----------------|------------|
| **복구 지연** | 배치 주기(1분) | 폴러 주기(수 초) |
| **복구 대상 식별** | Payment 상태 기반 (REQUESTED/PENDING/UNKNOWN) | Outbox 상태 기반 (PENDING) — 명시적 |
| **의도 보존** | 암묵적 (REQUESTED = "PG를 호출하려 했다") | 명시적 (Outbox 레코드 = "PG를 호출해야 한다") |
| **구현 복잡도** | 낮음 | 중간 (Outbox 테이블 + 폴러 추가) |
| **MSA 전환 시** | 서비스별 배치 각각 구현 | 이벤트 발행으로 자연스럽게 전환 |
| **신뢰성** | 높음 | 매우 높음 |

### 9.5 트레이드오프 분석

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. 배치 복구만 유지 | 단순, 이미 설계됨 | 복구 지연 1분, 의도가 암묵적 |
| **B. Outbox + 배치 복구 병행** | **즉시 복구 + 최종 안전망, MSA-ready** | Outbox 테이블 + 폴러 추가 구현 |
| C. Outbox로 배치 대체 | 깔끔한 단일 복구 경로 | 배치의 "전수 스캔" 안전망 제거 |

**결정: B. Outbox + 배치 복구 병행**

**근거**:
- Outbox는 정상 경로에서 PG 호출 누락을 **수 초 내에 감지하고 재시도**
- 배치 복구는 Outbox 폴러 자체가 장애일 때의 **최종 안전망**으로 유지
- MSA 전환 시 Outbox → 이벤트 발행(Kafka 등)으로 자연스럽게 진화 가능
- 현재 모노리스에서도 "PG 호출 의도"를 명시적으로 기록하는 것은 운영상 가치가 있음

### 9.6 Outbox 폴러 동작 흐름

```
[스케줄러: 5초 주기]
  1. PaymentOutbox에서 status = 'PENDING' 조회
  2. 각 건에 대해:
     a. Payment 현재 상태 확인
        - 이미 PAID/FAILED → Outbox PROCESSED 처리 (다른 경로로 해결됨)
     b. PG 상태 확인 (GET /api/v1/payments?orderId={orderId})
        - PG에 기록 있음 → 해당 transactionKey로 추적, Outbox PROCESSED
        - PG에 기록 없음 → PG 결제 요청 (POST) 실행
     c. retry_count 증가, 최대 3회 초과 시 Outbox FAILED + 알림
```

**멱등성 보장**: Outbox 폴러도 PG 호출 전에 반드시 PG 상태 확인 (5.1 수동 Retry와 동일 원칙)

---

## 10. Fallback 지점 전수 점검

### 10.1 점검 범위

주문/결제 흐름에서 발생할 수 있는 **모든 실패 시나리오**에 대해
사용자에게 어떤 응답을 줄 것인지, 시스템은 어떻게 복구할 것인지를 구체적으로 정의한다.

> **범위**: 주문 및 결제 Fallback만 포함. 상품 전시(조회) Fallback은 이번 과제 범위 외.

### 10.2 Fallback 지점 맵

```
[사용자 결제 요청]
       │
   ┌── FB1. 내부 검증 실패 ──→ 즉시 에러 응답 (400)
       │
   ┌── FB2. Primary PG 요청 실패 (Retry 소진)
   │   └── FB2-1. Fallback PG(Toss) 시도
   │       ├── 성공 → 정상 흐름
   │       └── FB2-2. Fallback PG도 실패 → UNKNOWN 저장 + "확인 중" 응답
       │
   ┌── FB3. PG 응답 수신 후 내부 저장 실패 ──→ 로그 + 배치 복구
       │
   ┌── FB4. 비동기 처리 결과 FAILED ──→ 정상 실패 처리 (재고 복원)
       │
   ┌── FB5. 콜백 미수신 ──→ 배치 복구 (자동)
       │
   ┌── FB6. 콜백 수신 후 내부 처리 실패 ──→ 배치 복구 (자동)
       │
   ┌── FB7. 유령 결제 (타임아웃인데 PG 성공) ──→ 콜백 + 배치 이중 복구
```

### 10.3 Fallback 지점별 대응 전략

| # | 실패 지점 | 대응 전략 | 사용자 응답 | 복구 방법 |
|---|----------|----------|-----------|----------|
| **FB1** | 내부 검증 실패 (주문 없음, 이미 결제됨) | 즉시 에러 반환 | `400` "주문 정보를 확인해주세요" | 복구 불필요 (사용자 재시도) |
| **FB2** | Primary PG 실패 (Retry 3회 소진) | **Fallback PG(Toss)로 전환** | 사용자 인지 없이 내부 전환 | 자동 (PG 전환) |
| **FB2-1** | Fallback PG(Toss)도 실패 | UNKNOWN 저장 + 안내 | `200` "결제 확인 중입니다" | Outbox + 배치 |
| **FB3** | PG 응답 OK, 내부 DB 저장 실패 | 로그 + Outbox PENDING 유지 | `500` "일시적 오류" | Outbox 폴러 재처리 |
| **FB4** | PG 비동기 FAILED (한도초과/잘못된 카드) | FAILED + 재고 복원 | 주문 상세에서 확인 | 정상 흐름 (복구 불필요) |
| **FB5** | 콜백 미수신 | PENDING/UNKNOWN 유지 | 주문 상태 "결제 확인 중" | 배치 1분 주기 복구 |
| **FB6** | 콜백 수신 후 내부 처리 실패 | 로그 남김 | 주문 상태 미변경 | 배치 복구 |
| **FB7** | 유령 결제 | UNKNOWN 유지 | "결제 확인 중" | 콜백 + 배치 이중 복구 |

### 10.4 기존 Fallback 대비 개선 사항

| 개선 영역 | 기존 (05 설계) | 개선안 | 근거 |
|----------|---------------|--------|------|
| **PG 전면 장애** | CB Open → UNKNOWN + "확인 중" | **Fallback PG(Toss)로 자동 전환** → 결제 성공률 유지 | 사용자 경험 보호 |
| **PG 호출 누락** | 배치 1분 주기 복구 | **Outbox 5초 주기 재시도** + 배치 안전망 | 복구 지연 최소화 |
| **내부 저장 실패** | 별도 대응 없음 | **로그 + Outbox 기반 재처리** | 데이터 유실 방지 |
| **Fallback 응답** | 단일 메시지 | **실패 유형별 구체적 안내 메시지** | UX 개선 |

---

## 11. Multi-PG Fallback 아키텍처

### 11.1 왜 Multi-PG가 필요한가?

현재 설계에서 PG 전면 장애 시:
- CB가 Open → 모든 결제 요청이 즉시 Fallback
- 사용자는 "확인 중" 메시지만 받음 → **결제 전환율 0%**
- PG 복구까지 모든 매출이 멈춤

대규모 이커머스에서 **단일 PG 의존은 SPoF(Single Point of Failure)**이다.
PG가 완전히 장애 나도 다른 PG로 결제를 이어갈 수 있어야 한다.

### 11.2 아키텍처 결정

```
[결제 요청]
     │
     ▼
[PG Router (Strategy)]
     │
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

### 11.3 PG 추상화 설계 (Strategy Pattern)

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
public class SimulatorPgClient implements PgClient { ... }

@Component
public class TossSandboxPgClient implements PgClient { ... }
```

```java
@Component
public class PgRouter {
    private final List<PgClient> pgClients;  // 우선순위 순

    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        for (PgClient client : pgClients) {
            try {
                return client.requestPayment(request);  // CB + Retry 적용
            } catch (Exception e) {
                log.warn("PG [{}] 실패, 다음 PG 시도", client.getProviderName(), e);
            }
        }
        throw new AllPgFailedException();  // → Fallback으로 연결
    }
}
```

### 11.4 왜 Strategy Pattern인가?

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. if/else로 PG 분기 | 빠른 구현 | PG 추가 시 코드 수정, OCP 위반 |
| **B. Strategy Pattern** | **PG 추가 시 구현체만 추가, 테스트 용이** | 인터페이스 설계 필요 |
| C. Abstract Factory | 유연한 생성 | 현재 2개 PG에 과도한 추상화 |

**결정: B. Strategy Pattern**

**근거**:
- PG 추가/제거가 기존 코드 변경 없이 가능 (OCP)
- 각 PG별 독립적인 CB/Retry 설정 가능
- 테스트 시 Mock PG 주입 용이
- 2개 PG로 시작하므로 Factory까지는 불필요

### 11.5 Toss Payments Sandbox 연동

| 항목 | 값 |
|------|-----|
| 환경 | Sandbox (테스트) |
| 인증 | Test Secret Key (Base64) |
| 결제 승인 API | `POST /v1/payments/confirm` |
| 결제 조회 API | `GET /v1/payments/{paymentKey}` |
| 결제 방식 | 동기 (요청 → 즉시 응답) |
| 멱등성 | `Idempotency-Key` 헤더 지원 |

> **주의**: PG Simulator는 **비동기** (요청 → PENDING → 콜백), Toss는 **동기** (요청 → 즉시 승인/실패).
> PgClient 인터페이스는 이 차이를 추상화해야 한다.

### 11.6 PG별 차이 추상화

| 항목 | PG Simulator | Toss Sandbox |
|------|-------------|-------------|
| 결제 방식 | 비동기 (콜백) | 동기 (즉시) |
| 멱등성 | 미지원 (수동 보장) | Idempotency-Key 지원 |
| 응답 | PENDING → 콜백 | SUCCESS/FAILED 즉시 |
| 콜백 필요 | O | X |

**추상화 전략**:

```
PgClient.requestPayment() 의 반환값:
- PENDING: PG가 비동기 처리 중 (콜백 대기 필요)
- SUCCESS: 즉시 승인 완료
- FAILED: 즉시 거부

→ Simulator: 항상 PENDING 반환 (콜백으로 최종 확정)
→ Toss: SUCCESS 또는 FAILED 즉시 반환 (콜백 불필요)
→ PaymentFacade는 반환값에 따라 분기:
  - PENDING → Payment(PENDING) + 콜백 대기
  - SUCCESS → Payment(PAID) + 주문 확정
  - FAILED → Payment(FAILED) + 재고 복원
```

### 11.7 PG별 CB/Retry 독립 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgSimulator:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        # ... Simulator 전용 설정
      pgToss:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
        # ... Toss 전용 설정
  retry:
    instances:
      pgSimulatorRetry:
        max-attempts: 3
        # ... Simulator 전용 설정
      pgTossRetry:
        max-attempts: 2  # Toss는 안정적이므로 적게
        # ... Toss 전용 설정
```

### 11.8 Fallback PG 전환 판단 기준

| 시나리오 | Primary PG | Fallback PG 전환? |
|----------|-----------|-----------------|
| Retry 3회 소진 (일시적 실패) | 실패 | **전환** |
| CB Open (전면 장애) | 즉시 차단 | **전환** |
| 400 에러 (잘못된 요청) | 실패 | **전환하지 않음** — 요청 자체가 잘못됨 |
| PG 비동기 처리 FAILED (한도초과) | 비즈니스 실패 | **전환하지 않음** — PG 문제가 아님 |

**근거**: Fallback PG 전환은 "PG 인프라 장애"에만 적용. 비즈니스 로직 실패(한도초과, 잘못된 카드)는 다른 PG에서도 동일하게 실패한다.

### 11.9 비동기→동기 PG Fallback 시 중복 결제 위험

**핵심 문제**: Simulator(비동기)에서 **타임아웃**이 발생하면, PG 측에서는 결제가 진행 중일 수 있다.
이 상태에서 Toss(동기)로 Fallback하면 **같은 주문에 대해 2건의 결제가 발생**한다.

```
[Simulator] POST 결제 요청 → 타임아웃 (그러나 PG에서는 PENDING으로 저장됨)
[Toss]      POST 결제 요청 → SUCCESS (즉시 승인)
... 3초 후 ...
[Simulator] 콜백 → SUCCESS (두 번째 결제 성공 통보)
→ 결과: 같은 주문에 대해 Simulator + Toss 모두 결제 완료 = 중복 결제
```

#### 대응 방안

| 선택지 | 동작 | 장점 | 단점 |
|--------|------|------|------|
| A. Fallback 전환 전 Primary PG 상태 확인 | `GET /payments?orderId=xxx` 호출 후 판단 | 중복 결제 방지 | 추가 API 호출 1회 (수십ms) |
| **B. 타임아웃 실패 시 Fallback 전환하지 않음** | **500/연결실패만 Fallback, 타임아웃은 UNKNOWN 처리** | **단순, 안전** | 타임아웃 시 Toss 활용 불가 |
| C. 결제 전 항상 양쪽 PG 조회 | 모든 PG에 상태 확인 | 확실한 방지 | 과도한 호출, 지연 증가 |

**결정: B. 타임아웃 실패 시 Fallback 전환하지 않음**

**근거**:
- 타임아웃은 "PG에 요청이 도달했을 가능성"이 있는 실패 → 다른 PG로 전환하면 중복 위험
- 500 에러 / 연결 실패는 "PG에 요청이 도달하지 않은" 실패 → 안전하게 다른 PG 시도 가능
- UNKNOWN 상태 + Outbox/배치 복구로 타임아웃 건은 자동 해소
- 선택지 A도 유효하지만, PG 상태 확인 API 자체가 타임아웃 날 수 있어 복잡도 증가

#### Fallback 전환 최종 판단 매트릭스

| 실패 유형 | PG 도달 가능성 | Fallback 전환 | 이유 |
|----------|-------------|-------------|------|
| **ConnectException** (연결 실패) | 없음 | **전환** | PG에 요청 자체가 안 감 |
| **500 에러** (서버 에러) | 낮음 (처리 전 실패) | **전환** | PG가 요청을 처리하지 못함 |
| **SocketTimeoutException** (읽기 타임아웃) | **있음** | **전환하지 않음** | PG에서 처리 중일 수 있음 |
| **CB Open** (서킷 오픈) | - | **전환** | PG 전면 장애 판단 |
| **400 에러** (잘못된 요청) | - | **전환하지 않음** | 요청 자체가 잘못됨 |

---

## 12. Fallback 전략 체계 — 쿠팡 수준 설계

### 12.1 "진짜 Fallback"의 정의

```
❌ 단순 Fallback: 에러를 잡아서 "잠시 후 다시 시도해주세요" 메시지 반환
✅ 진짜 Fallback: 장애가 발생한 경로 대신 대체 경로로 비즈니스를 계속 수행
```

쿠팡 규모에서 "결제가 안 됩니다"는 **분당 수억 원의 매출 손실**이다.
에러 메시지를 예쁘게 주는 것은 Fallback이 아니다. **돈이 계속 들어오게 하는 것**이 Fallback이다.

### 12.2 결제 흐름 전체 Fallback 맵

```
[사용자 결제 요청]
       │
   [1] 주문 검증
       │
   [2] Payment + Outbox 저장 ──DB 장애──→ [FB-WAL] 로컬 WAL에 임시 기록
       │
   [3] PG 결제 요청 ──PG 장애──→ [FB-PG] 대체 PG(Toss)로 자동 전환
       │
   [4] PG 응답 저장 ──DB 장애──→ [FB-WAL] transactionKey를 로컬에 기록
       │
   [5] 비동기 대기
       │
   [6] 콜백 수신 ──미수신──→ [FB-POLL] 능동적 폴링으로 전환
       │
   [7] 콜백 처리 ──내부 장애──→ [FB-DLQ] 콜백 데이터 보존 → 재처리
       │
   [8] 결제 실패 → 재고 복원 ──실패──→ [FB-COMP] 보상 이벤트 큐잉
```

### 12.3 FB-PG: PG 인프라 장애 → Multi-PG Routing

> 이미 Section 11에서 설계 완료.

| 장애 | 대체 경로 | 효과 |
|------|----------|------|
| Simulator 전면 장애 | Toss Sandbox로 자동 전환 | 결제 계속 가능 |
| Simulator 타임아웃 | 전환하지 않음 (중복 결제 방지) | UNKNOWN → 자동 복구 |

### 12.4 FB-POLL: 콜백 채널 장애 → Polling Hybrid

#### 현재 설계의 한계

```
콜백 미수신 → 배치 1분 주기 복구
→ 최악의 경우 사용자는 1분간 "결제 확인 중" 상태에 머무름
```

사용자 입장에서 1분은 **매우 긴 시간**이다. 결제했는데 1분간 결과를 모르면 불안해서 재결제를 시도한다.

#### 개선: 콜백 + 능동적 폴링 하이브리드

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

| 선택지 | 복구 지연 | 구현 복잡도 |
|--------|----------|-----------|
| A. 배치만 (현재) | 최대 1분 | 낮음 |
| **B. Delayed Task + 배치** | **최대 10초** | 중간 |
| C. WebSocket 실시간 | 즉시 | 높음 (인프라 변경) |

**결정: B. Delayed Task + 배치 (이중 안전망)**

**구현 방식**:

```java
// PG 응답(PENDING) 수신 직후
taskScheduler.schedule(
    () -> paymentRecoveryService.checkAndRecover(paymentId),
    Instant.now().plusSeconds(10)  // 10초 후 실행
);
```

**근거**:
- PG 비동기 처리 최대 5초 + 콜백 전송 시간 → 10초면 콜백이 왔어야 함
- 10초 후에도 없으면 콜백 유실 가능성 높음 → 능동적으로 확인
- 배치(1분)는 Delayed Task 자체의 장애(서버 재시작 등) 시 최종 안전망

### 12.5 FB-WAL: 내부 DB 장애 → Local Write-Ahead Log

#### 핵심 문제

PG에서 결제가 성공했는데 내부 DB에 기록을 못 하면:
- **내부에 Payment 레코드 자체가 없음** → 배치 복구도 불가 (조회 대상이 없으니까)
- 고객 돈은 빠졌는데 주문은 결제 안 된 상태 → **최악의 UX**

```
PG: "결제 성공, transactionKey = xxx"
내부 DB: (장애로 저장 실패)
배치: (Payment 레코드가 없으니 복구 대상 자체를 모름)
→ 유령 결제: 감지도, 복구도 불가능
```

#### 대응: Local WAL (Write-Ahead Log)

```
PG 응답 수신 즉시:
  1. [WAL] 로컬 파일/Redis에 {orderId, transactionKey, pgResponse} 기록
  2. [DB]  Payment 상태 업데이트 시도
     - 성공 → WAL 레코드 삭제
     - 실패 → WAL에 남아있음

[WAL Recovery 스케줄러]
  WAL에 남아있는 레코드 → DB에 반영 재시도
  → 성공 시 WAL 삭제
```

| 선택지 | 저장 위치 | 장점 | 단점 |
|--------|----------|------|------|
| **A. 로컬 파일 WAL** | **서버 로컬 디스크** | **DB 무관하게 저장 가능, 단순** | 서버 디스크 장애 시 유실, 다중 서버 시 분산 |
| B. Redis WAL | Redis | 빠름, 서버 간 공유 | Redis 장애 시 유실 (DB와 동시 장애 시) |
| C. Kafka WAL | Kafka | 높은 내구성 | 인프라 추가 필요 |

**결정: A. 로컬 파일 WAL (현재 과제) / B. Redis WAL (프로덕션)**

**근거**:
- 현재 과제: 단일 서버이므로 로컬 파일로 충분
- 프로덕션(쿠팡): Redis 또는 Kafka로 서버 간 공유 필요
- 핵심은 "DB와 독립적인 저장소에 PG 응답을 먼저 기록"하는 것

### 12.6 FB-DLQ: 콜백 처리 장애 → Dead Letter Queue

#### 핵심 문제

```
PG 콜백 수신 → 내부 처리 중 예외 발생 → PG에게 500 응답
PG: 콜백 재시도하지 않음
→ 콜백 데이터 유실: 결제 결과를 다시 받을 방법이 없음
```

배치가 PG 상태 확인 API로 복구할 수 있지만, **콜백에 포함된 상세 정보**(실패 사유 등)는 유실될 수 있다.

#### 대응: 콜백 DLQ 테이블

```
콜백 수신 → [1단계] callback_inbox 테이블에 원본 저장 (status: RECEIVED)
          → [2단계] 비즈니스 처리 (Payment/Order 상태 전이)
          → [성공] callback_inbox status → PROCESSED
          → [실패] callback_inbox에 남아있음 (RECEIVED)

[DLQ 재처리 스케줄러]
  callback_inbox에서 status = 'RECEIVED' + 생성 후 N초 경과 → 재처리 시도
```

```sql
CREATE TABLE callback_inbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_key VARCHAR(50) NOT NULL,
    order_id        VARCHAR(50) NOT NULL,
    payload         TEXT NOT NULL,           -- 콜백 원본 JSON
    status          VARCHAR(20) NOT NULL,    -- 'RECEIVED' / 'PROCESSED' / 'FAILED'
    received_at     DATETIME NOT NULL,
    processed_at    DATETIME,
    retry_count     INT DEFAULT 0,
    error_message   VARCHAR(500)
);
```

**핵심**: PG에게는 **항상 200 OK를 먼저 반환**하고, 내부 처리는 비동기로 수행.
이렇게 하면 PG 측에서 타임아웃이 발생하지 않고, 우리는 원본 데이터를 보존한 채 재처리할 수 있다.

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. 콜백 실패 시 배치로 PG 재조회 | 기존 인프라 활용 | 콜백 상세 정보 유실 |
| **B. Callback Inbox (DLQ)** | **원본 보존, 재처리 가능, 감사 로그** | 테이블 하나 추가 |
| C. Kafka DLQ | 높은 처리량 | 인프라 추가 |

**결정: B. Callback Inbox (DB 테이블 DLQ)**

**근거**:
- 콜백 처리량이 초당 수천 건이 아닌 이상 DB 테이블로 충분
- 콜백 원본을 보존하므로 디버깅, 감사(audit) 용도로도 활용
- PG에게 항상 200 먼저 반환 → 콜백 유실 원천 차단

### 12.7 FB-COMP: 재고 복원 실패 → 보상 트랜잭션 큐

#### 현재 상황

모노리스 + 같은 DB → 결제 실패 시 재고 복원은 같은 TX에서 원자적으로 처리.
**현재 구조에서는 이 Fallback이 불필요하다.**

#### MSA 전환 시 필요

```
[Payment Service] 결제 FAILED 확정
  → [Stock Service] 재고 복원 요청 (HTTP/이벤트)
  → Stock Service 장애 → 재고 복원 실패
  → 고객은 결제도 안 됐는데 재고는 차감된 상태
```

| 선택지 | 동작 | 적용 시점 |
|--------|------|----------|
| A. 같은 TX (현재) | Payment + Stock 같은 DB TX | **모노리스** |
| B. 보상 이벤트 큐 | 실패 시 compensation_events에 기록, 스케줄러가 재시도 | **MSA 전환 시** |
| C. Saga Pattern | Orchestrator 또는 Choreography | **MSA 대규모** |

**결정: 현재 A, MSA 전환 시 B**

### 12.8 FB-CARD: 카드사 장애 → 결제 수단 Fallback

#### 핵심 문제

특정 카드사(예: 삼성카드) 네트워크 장애 시:
- PG를 바꿔도 **같은 카드사면 동일 실패**
- Multi-PG Fallback으로는 해결 안 됨

#### 대응: 카드사별 실패율 모니터링 + 안내

```
[카드사 실패율 모니터링]
  최근 N건 중 특정 카드사 실패율 > 임계치
  → 해당 카드사로 결제 시도 시: "해당 카드사 결제가 일시적으로 불안정합니다.
     다른 카드 또는 결제 수단을 이용해주세요."
```

| 선택지 | 장점 | 단점 |
|--------|------|------|
| A. 카드사 장애 무시 | 단순 | 사용자가 계속 실패 경험 |
| **B. 실패율 기반 사전 안내** | **불필요한 시도 방지, UX 개선** | 모니터링 로직 추가 |
| C. BIN 기반 자동 라우팅 | 완전 자동화 | 카드사별 PG 계약 필요 |

**결정: 현재 과제 범위 외. 설계만 기록.**

쿠팡에서는 B + C를 조합하여 카드사별 Circuit Breaker를 운영한다.

### 12.9 Fallback 전략 구현 범위 결정

| # | Fallback 전략 | 설명 | 현재 과제 | MSA/프로덕션 |
|---|-------------|------|----------|-------------|
| **FB-PG** | Multi-PG Routing | 대체 PG로 자동 전환 | **구현** | N개 PG 확장 |
| **FB-POLL** | Polling Hybrid | 콜백 미수신 시 능동적 조회 | **구현** | Delayed Queue (Kafka) |
| **FB-WAL** | Local WAL | DB 장애 시 PG 응답 로컬 보존 | **구현** (파일) | Redis/Kafka WAL |
| **FB-DLQ** | Callback Inbox | 콜백 처리 실패 시 원본 보존 | **구현** (DB 테이블) | Kafka DLQ |
| **FB-COMP** | 보상 트랜잭션 큐 | 재고 복원 실패 시 재시도 | 불필요 (모노리스) | Saga Pattern |
| **FB-CARD** | 카드사별 모니터링 | 카드사 장애 시 사전 안내 | 설계만 기록 | CB per 카드사 |

### 12.10 Fallback 계층 구조 (최종)

```
[결제 요청]
     │
     ▼
[1차 방어] Timeout → 개별 요청 시간 제한
     │
[2차 방어] Retry → 일시적 실패 재시도 (멱등성 보장)
     │
[3차 방어] Circuit Breaker → 반복 실패 시 호출 차단
     │
[4차 방어] Multi-PG Fallback → 대체 PG로 자동 전환
     │
[5차 방어] Polling Hybrid → 콜백 실패 시 능동적 확인
     │
[6차 방어] Callback DLQ → 콜백 데이터 보존 + 재처리
     │
[7차 방어] Local WAL → DB 장애 시 PG 응답 보존
     │
[최종 방어] UNKNOWN + Outbox + 배치 → 모든 방어가 뚫려도 최종 복구
```

**핵심**: 각 계층은 이전 계층이 실패했을 때 작동한다.
7계층을 모두 뚫고 실패하는 경우는 **내부 DB + 로컬 디스크 + 모든 PG + 배치 서버가 동시에 장애**인 상황이며,
이 경우에만 수동 운영 개입이 필요하다.

---

## 13. Circuit Breaker 세분화 점검

### 13.1 세분화 원칙

서킷브레이커를 촘촘히 나누는 이유: **장애 격리**.
하나의 CB에 여러 호출을 묶으면, 특정 호출의 장애가 관계없는 호출까지 차단한다.

```
CB 분리 기준 = 장애 격리 경계
"A가 죽었을 때 B까지 차단되면 안 되는가?" → 그렇다면 CB를 분리해야 한다.
```

### 13.2 현재 설계의 문제점

현재 05에서 PG별 CB(`pgSimulator`, `pgToss`)만 분리했다.
하지만 같은 PG 내에서도 **결제 요청(POST)**과 **상태 조회(GET)**를 하나의 CB로 묶으면:

```
[치명적 시나리오]
1. PG 결제 요청(POST) 대량 실패 → CB Open
2. CB Open → 상태 조회(GET)도 차단됨
3. 상태 조회 차단 → Outbox 폴러, 배치, Polling Hybrid 전부 PG 조회 불가
4. → 모든 복구 경로 마비
5. → UNKNOWN/PENDING 결제건이 영원히 미확정
```

**결제 요청이 안 되는 것**은 Fallback PG로 넘기면 된다.
**상태 조회까지 차단되는 것**은 복구 자체가 불가능해지므로 치명적이다.

### 13.3 CB 세분화 설계

#### 분리 기준: PG × API 유형

| CB 인스턴스 | 대상 | 장애 시 영향 |
|------------|------|------------|
| `pgSimulator-request` | POST /payments (결제 요청) | 결제 요청만 차단 → Fallback PG 전환 |
| `pgSimulator-status` | GET /payments/{key}, GET /payments?orderId= (상태 조회) | 복구 로직만 차단 → 배치가 재시도 |
| `pgToss-request` | POST /v1/payments/confirm (결제 승인) | Toss 결제만 차단 → 최종 Fallback(UNKNOWN) |
| `pgToss-status` | GET /v1/payments/{paymentKey} (상태 조회) | Toss 복구만 차단 |

#### 왜 결제 요청과 상태 조회를 분리하는가?

```
PG 내부 아키텍처 (일반적):
  [결제 처리 서버] ← POST 요청 (쓰기 부하)
  [조회 서버/읽기 복제본] ← GET 요청 (읽기 부하)
```

- PG의 **결제 처리 서버**가 과부하로 죽어도 **조회 서버**는 정상일 수 있음
- 결제 요청 CB가 Open이어도 상태 조회 CB는 Closed → **복구 로직 계속 동작**
- 하나로 묶으면 쓰기 장애가 읽기까지 전파 → 장애 격리 실패

#### 추가 분리 검토: 실시간 vs 배치

| 선택지 | 구조 | 장점 | 단점 |
|--------|------|------|------|
| A. 상태 조회 CB 하나로 통합 | `pgSimulator-status` 하나 | 단순 | 배치가 대량 호출 → CB Open → 실시간 폴링도 차단 |
| **B. 실시간 / 배치 분리** | `pgSimulator-status-realtime` + `pgSimulator-status-batch` | **배치 장애가 실시간 복구에 영향 없음** | CB 인스턴스 증가 |
| C. 분리 안 함 | 그대로 | - | 장애 전파 |

**결정: B. 실시간 / 배치 분리**

**근거**:
- 배치는 대량의 미확인 건을 한꺼번에 조회 → PG 조회 API에 부하를 줄 수 있음
- 배치가 PG 조회 API를 과부하시켜 CB를 Open시키면, 실시간 Polling Hybrid와 수동 복구 API도 차단
- 분리하면: 배치 CB Open → 배치만 중단, 실시간 복구는 계속 동작

### 13.4 최종 CB 인스턴스 목록

```
[PG Simulator]
  pgSimulator-request           # 결제 요청 (POST)
  pgSimulator-status-realtime   # 상태 조회 - 실시간 (Polling Hybrid, 수동 복구, Outbox 폴러)
  pgSimulator-status-batch      # 상태 조회 - 배치 (1분 주기 대량 조회)

[Toss Sandbox]
  pgToss-request                # 결제 승인 (POST)
  pgToss-status-realtime        # 상태 조회 - 실시간
  pgToss-status-batch           # 상태 조회 - 배치
```

총 **6개 CB 인스턴스**.

### 13.5 CB별 설정 차별화

각 CB의 성격에 따라 임계치를 다르게 설정한다.

| CB 인스턴스 | 실패율 임계치 | 윈도우 크기 | Open 유지 | 근거 |
|------------|-------------|-----------|----------|------|
| `pgSimulator-request` | 50% | 10 | 10s | 정상 PG 실패율(40%) + 여유 10%p. Retry 후 최종 실패율 ~6.4% 기준 |
| `pgSimulator-status-realtime` | 50% | 10 | 5s | 조회는 빠르게 복구 시도. 5초만 대기 후 Half-Open |
| `pgSimulator-status-batch` | 70% | 20 | 30s | 배치는 대량 호출이므로 일시적 실패에 과민 반응 방지. 윈도우 크게, 임계치 높게, Open 길게 |
| `pgToss-request` | 50% | 10 | 15s | Toss는 안정적이므로 Open 시 복구 여유를 더 줌 |
| `pgToss-status-realtime` | 50% | 10 | 5s | 실시간 복구 빠르게 |
| `pgToss-status-batch` | 70% | 20 | 30s | 배치 보호 |

**차별화 근거**:
- **request CB**: Fallback PG가 있으므로 적극적으로 Open해도 됨 (다른 PG로 전환)
- **status-realtime CB**: 복구 경로이므로 빠르게 Half-Open 시도 (5초)
- **status-batch CB**: 대량 호출 특성상 일시적 실패가 많을 수 있으므로 보수적 운영

### 13.6 CB 세분화 설정 (Resilience4j)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      # --- PG Simulator ---
      pgSimulator-request:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50

      pgSimulator-status-realtime:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 1s    # 조회는 빨라야 함
        slow-call-rate-threshold: 50

      pgSimulator-status-batch:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        failure-rate-threshold: 70
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 70

      # --- Toss Sandbox ---
      pgToss-request:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 3s    # Toss 응답이 Simulator보다 안정적
        slow-call-rate-threshold: 50

      pgToss-status-realtime:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 1s
        slow-call-rate-threshold: 50

      pgToss-status-batch:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        failure-rate-threshold: 70
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 70
```

### 13.7 장애 격리 검증 매트릭스

| 장애 시나리오 | 차단되는 CB | 영향받는 기능 | 영향받지 않는 기능 |
|-------------|-----------|------------|----------------|
| Simulator 결제 처리 장애 | `pgSimulator-request` | Simulator 결제 요청 | **Toss 결제, 모든 상태 조회, 모든 복구** |
| Simulator 조회 서버 장애 | `pgSimulator-status-*` | Simulator 상태 조회 | **모든 결제 요청, Toss 조회** |
| 배치가 Simulator 과부하 유발 | `pgSimulator-status-batch` | 배치 조회만 | **실시간 폴링, 수동 복구, 결제 요청** |
| Toss 전면 장애 | `pgToss-request` + `pgToss-status-*` | Toss 전체 | **Simulator 전체** |
| 모든 PG 결제 장애 | `*-request` 전부 | 모든 결제 | **모든 상태 조회 → 복구 가능** |

**핵심 확인: "결제가 안 되더라도 복구는 항상 동작한다."**

### 13.8 쿠팡 수준 추가 세분화 (설계만)

프로덕션에서 더 촘촘하게 나눌 수 있는 CB:

| CB | 대상 | 근거 |
|----|------|------|
| `card-samsung` | 삼성카드 경유 결제 | 특정 카드사 장애 격리 |
| `card-hyundai` | 현대카드 경유 결제 | 카드사별 독립 CB |
| `pg-nicepay-request` | 나이스페이 결제 | PG사별 독립 CB |
| `payment-callback-process` | 콜백 내부 처리 | 콜백 처리 장애가 다른 기능에 전파 방지 |

**현재 과제에서는 6개 CB로 충분. 카드사별 CB는 BIN 데이터와 카드사별 트래픽이 확보된 후 추가.**

### 13.9 배치 CB 임계치 재검토 — 70% → 50%

**문제**: 70%는 "PG에 10건 보내서 7건 실패할 때까지 계속 호출"하는 것.
이미 PG는 과부하 상태인데 계속 쏟아부으면 PG 복구를 방해한다.

**근본 원인**: 배치가 PG를 과부하시킬 수 있다는 전제 자체가 잘못됨.
배치 호출 속도를 제어(Rate Limiter)하면 PG throttling 가능성이 사라지고, CB 임계치를 낮출 수 있다.

```
[기존] 배치 무제한 호출 → PG throttling 예상 → CB 70%로 과보정
[개선] 배치 Rate Limiter(초당 10건) → throttling 없음 → CB 50%
```

**결정: 배치 CB도 50%로 통일. Rate Limiter 추가.**

---

## 14. 비동기 결제 고유 Fallback 분석

### 14.1 비동기 결제의 본질적 위험

```
[동기] 요청 → 결과 즉시 확정. 끝.
[비동기] 요청 → PENDING → ???(1~5초) → 콜백 → 결과 확정
                          ↑ 불확실 구간
```

기존 Fallback은 "PG 요청 시점"에 집중. 비동기 결제의 **"요청 성공 이후 불확실 구간"**에 대한 Fallback이 빠져있었다.

### 14.2 비동기 고유 장애 시나리오

| # | 장애 | 대응 상태 | 빈 곳 |
|---|------|----------|------|
| A1 | 콜백 영구 미수신 | Polling 10초 + 배치 1분 | ✓ |
| A2 | PG에서 영원히 PENDING (PG 크래시) | 배치 감지 | **PENDING 최종 처리 정책 필요** |
| A3 | 콜백이 왔는데 status가 아직 PENDING | 없음 | **PENDING 콜백 무시 정책 필요** |
| A4 | 콜백 채널 전체 불안정 | 없음 | **비동기→동기 전환 Fallback 필요** |
| A5 | 불확실 구간에서 사용자 재결제 | UNIQUE(order_id) | ✓ |

### 14.3 A2. PENDING 최대 허용 시간

| 선택지 | 동작 | 판단 |
|--------|------|------|
| A. 무한 대기 | PG PENDING이면 계속 유지 | 고객 결제 영원히 미확정 |
| **B. 5분 초과 시 FAILED** | **5분 후 FAILED + 재고 복원** | **고객 해방, 재결제 가능** |
| C. 수동 운영 | 운영자 판단 | 운영 부담 |

**결정: B. PENDING 최대 허용 5분**

- PG 처리 최대 5초 × 안전 마진 = 5분이면 충분
- FAILED 후 PG 뒤늦은 SUCCESS 콜백 → 조건부 UPDATE가 무시 (이미 FAILED)
- 불일치 해소: PG 대사(reconciliation) 운영 프로세스

### 14.4 A3. PENDING 상태 콜백 처리

**콜백 status가 PENDING이면 상태 전이하지 않고 무시.** SUCCESS/FAILED만 처리.

### 14.5 A4. 콜백 채널 불안정 → 동기 PG 전환

비동기 PG의 가장 근본적인 Fallback: **불확실 구간 자체를 제거**.

```
[콜백 신뢰율 모니터링]
최근 N건 "PENDING 응답 → 10초 내 콜백 수신" 비율 추적
→ 50% 미만: 콜백 채널 불안정 판단
→ 이후 결제를 Toss(동기)로 우선 라우팅
→ 동기 PG = 요청 즉시 결과 확정 = 불확실 구간 없음
```

| 선택지 | 동작 | 판단 |
|--------|------|------|
| A. Polling으로 커버 | 매번 10초 후 폴링 | UX 지연 |
| **B. 콜백 신뢰율 기반 PG 전환** | **동기 PG로 전환 → 불확실 구간 제거** | 근본 해결 |

**결정: B**

### 14.6 → 05 반영 사항

| 반영 대상 | 내용 |
|----------|------|
| Section 7.4 | 배치 CB 임계치 70% → 50% + Rate Limiter 추가 |
| Section 8 | 비동기 결제 Fallback 섹션 추가 (A2~A4) |
| Section 10 | 배치 복구에 PENDING 최대 5분 정책 추가 |
| Section 9 | 콜백 처리에 PENDING 상태 무시 정책 추가 |
| Section 8.3 | 콜백 신뢰율 기반 PG 전환 로직 추가 |

---

## 15. Half-Open 전략 고도화

### 15.1 현재 설계의 문제점

```
CB Open → 10초 고정 대기 → Half-Open → 실제 결제 요청 2건으로 테스트 → Closed/Open
```

| 문제 | 설명 | 쿠팡 규모 영향 |
|------|------|-------------|
| **고정 대기 시간** | PG 2초에 복구 → 8초 낭비 / PG 30초 복구 → 10초마다 실패 반복 | 초당 수천 건 결제 기회 손실 or 복구 방해 |
| **고객을 실험 대상으로 사용** | Half-Open 테스트 = 실제 고객 결제 | PG 미복구 시 해당 고객만 불필요한 실패 경험 |
| **즉시 전량 복구 (Thundering Herd)** | 테스트 2건 성공 → 즉시 Closed → 전체 트래픽 폭주 | 겨우 살아난 PG 다시 과부하 → 재장애 |

### 15.2 개선 전략 3가지

#### 전략 1: Progressive Backoff (점진적 대기 시간)

고정 대기 대신, **Open이 반복될수록 대기 시간을 늘린다.**

```
1차 Open: 5초 → Half-Open (빠르게 복구 시도)
  실패 → 2차 Open: 10초 → Half-Open
  실패 → 3차 Open: 20초 → Half-Open
  실패 → 4차 Open: 40초 → Half-Open
  실패 → 5차+ Open: 60초 (cap) → Half-Open
```

| 선택지 | 대기 전략 | 장점 | 단점 |
|--------|----------|------|------|
| A. 고정 10초 (현재) | 항상 10초 | 단순 | 너무 짧거나 너무 길음 |
| **B. 지수 백오프** | **5s → 10s → 20s → 40s → 60s cap** | **짧은 장애 빠른 복구, 긴 장애 복구 방해 안 함** | 구현 복잡도 약간 증가 |
| C. 선형 증가 | 10s → 20s → 30s → ... | 점진적 | 장기 장애 시 증가 속도가 느림 |

**결정: B. 지수 백오프 (5초 시작, 60초 cap)**

**근거:**
- 단순 일시 장애(네트워크 flap): 5초 만에 복구 확인 → 최소 다운타임
- PG 재시작(30초~1분): 5→10→20초 시점에 복구 감지
- PG 전면 장애(수 분): 60초 간격으로 체크 → PG 부하 최소화
- Resilience4j 기본 제공은 아니지만, EventPublisher로 Open 횟수 추적하여 구현 가능

**구현:**

```java
@Component
public class ProgressiveBackoffCustomizer {
    private final Map<String, AtomicInteger> openCountMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void customize(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                .onStateTransition(event -> {
                    String name = cb.getName();
                    if (event.getStateTransition() == StateTransition.OPEN_TO_HALF_OPEN) {
                        // Half-Open 진입 시 — 다음 Open 대기 시간 계산용
                    }
                    if (event.getStateTransition() == StateTransition.HALF_OPEN_TO_OPEN) {
                        // Half-Open 실패 → 다시 Open — 카운트 증가
                        openCountMap.computeIfAbsent(name, k -> new AtomicInteger(0))
                            .incrementAndGet();
                    }
                    if (event.getStateTransition() == StateTransition.HALF_OPEN_TO_CLOSED) {
                        // 복구 성공 → 카운트 리셋
                        openCountMap.computeIfAbsent(name, k -> new AtomicInteger(0))
                            .set(0);
                    }
                });
        });
    }

    public Duration getWaitDuration(String cbName) {
        int count = openCountMap.getOrDefault(cbName, new AtomicInteger(0)).get();
        long seconds = Math.min(5L * (1L << count), 60L); // 5, 10, 20, 40, 60(cap)
        return Duration.ofSeconds(seconds);
    }
}
```

> **한계**: Resilience4j의 `wait-duration-in-open-state`는 정적 설정이다.
> 동적 변경은 CB를 재생성하거나, Custom CircuitBreaker로 구현해야 한다.
> 현재 과제에서는 고정 대기 + 이벤트 로깅으로 시작하고, 프로덕션에서 동적 변경을 적용한다.

#### 전략 2: Health Check Probe (헬스 체크 분리)

**실제 고객 요청 대신 별도 경량 요청으로 PG 상태를 확인한다.**

```
[기존] CB Open → 10초 → Half-Open → 실제 결제 요청 2건으로 테스트
                                      ↑ 고객이 실험 대상

[개선] CB Open → 5초 → Health Probe → PG 응답 OK → Half-Open → 실제 트래픽 허용
                        ↑ 별도 경량 요청 (고객 무관)
```

| PG | Health Check 방법 | 설명 |
|----|-------------------|------|
| Simulator | `GET /api/v1/payments?orderId=HEALTH_CHECK` | 존재하지 않는 orderId로 조회 → 200/404 응답이면 서버 살아있음 |
| Toss | `GET /v1/payments/HEALTH_CHECK` | 존재하지 않는 paymentKey → 404 응답이면 서버 살아있음 |

**핵심: 200이든 404든 "응답이 왔다"는 것 자체가 PG가 살아있다는 증거.**
500 에러나 타임아웃이면 아직 장애.

```java
@Component
public class PgHealthChecker {
    private final SimulatorFeignClient simulatorClient;

    /**
     * PG 서버가 살아있는지 경량 확인.
     * 실제 결제 요청이 아닌 조회 요청으로 확인하므로 부작용 없음.
     */
    public boolean isSimulatorHealthy() {
        try {
            simulatorClient.getPaymentByOrderId("HEALTH_CHECK");
            return true;  // 200 or 404 — 서버 응답함
        } catch (FeignException.NotFound e) {
            return true;  // 404 — 서버 살아있음, 데이터만 없음
        } catch (Exception e) {
            return false; // 타임아웃, 500, 연결 실패 — 서버 장애
        }
    }
}
```

```
[Health Probe 스케줄러]
CB가 Open 상태인 동안:
  1. Progressive Backoff 간격으로 Health Check 실행
  2. 응답 성공 → CB 수동 전환 (circuitBreaker.transitionToHalfOpenState())
  3. 응답 실패 → 대기 계속
```

| 선택지 | 테스트 대상 | 장점 | 단점 |
|--------|-----------|------|------|
| A. 실제 고객 요청 (현재) | 결제 요청 | 단순 | 고객이 실험 대상, 돈이 걸림 |
| **B. Health Check Probe** | **경량 조회 요청** | **고객 영향 없음, 부작용 없음** | Probe 스케줄러 추가 |
| C. PG 상태 페이지 구독 | PG 외부 시그널 | 가장 정확 | PG가 상태 페이지를 제공해야 함 |

**결정: B. Health Check Probe**

**근거:**
- 결제 요청(POST)은 부작용이 있다 (돈이 걸림). 테스트용으로 쓰면 안 됨
- 조회 요청(GET)은 멱등하고 부작용 없음 → 안전한 Health Check
- PG 상태 페이지는 외부 의존이므로 우리가 통제 불가

#### 전략 3: Phased Ramp-up (단계적 트래픽 복구)

Half-Open 테스트 성공 → 즉시 Closed 대신, **트래픽을 단계적으로 늘린다.**

```
[기존] Half-Open 2건 성공 → 즉시 Closed → 전량 트래픽 유입 → Thundering Herd

[개선]
  Health Probe 성공 → Phase 1: 트래픽 10% 허용
  Phase 1 성공 → Phase 2: 트래픽 50% 허용
  Phase 2 성공 → Phase 3: 100% (Closed)
```

| 선택지 | 복구 방식 | 장점 | 단점 |
|--------|----------|------|------|
| A. 즉시 전량 (현재) | 2건 성공 → Closed | 단순 | Thundering Herd |
| **B. 단계적 ramp-up** | **10% → 50% → 100%** | **PG 부하 점진적 증가, 재장애 방지** | 구현 복잡 |
| C. 고정 비율 | 항상 50%만 허용 | 안전 | 복구 완료 후에도 50%만 처리 |

**결정: 현재 과제는 A(즉시 전량) + Health Probe로 시작. 프로덕션에서 B 적용.**

**근거:**
- Resilience4j는 단계적 ramp-up을 기본 제공하지 않음
- 구현하려면 Custom CB 또는 앞단에 Rate Limiter를 동적으로 조절해야 함
- Multi-PG Fallback이 있으므로, 한 PG의 Thundering Herd가 발생해도 다른 PG가 받아줌
- 프로덕션에서는 Envoy/Istio 같은 서비스 메시에서 outlier detection + 단계적 복구 적용

### 15.3 CB 유형별 Half-Open 전략

| CB 유형 | Open → Half-Open 전환 | Half-Open 테스트 | 근거 |
|---------|---------------------|-----------------|------|
| `*-request` (결제) | **Health Probe** (경량 GET) | Probe 성공 → Half-Open → 실제 트래픽 2건 | 결제는 돈이 걸림, 고객을 실험 대상으로 쓰면 안 됨 |
| `*-status-realtime` (실시간 조회) | **Progressive Backoff** (5s→10s→20s→60s) | 실제 조회 요청 2건 | 읽기 전용, 멱등 → 실제 요청으로 테스트 OK |
| `*-status-batch` (배치 조회) | **Progressive Backoff** (10s→20s→40s→60s) | 실제 조회 요청 3건 | 배치는 급하지 않음, 넉넉하게 |

### 15.4 최종 Half-Open 흐름

```
[결제 요청 CB — pgSimulator-request]

  CB Open
    │
    ▼
  [Health Probe 스케줄러 시작]
    ├── 5초 후: GET /payments?orderId=HEALTH_CHECK
    │     ├── 응답 OK (200/404) → CB.transitionToHalfOpenState()
    │     │     → 실제 결제 요청 2건 허용
    │     │     → 2건 성공 → Closed (Open 카운트 리셋)
    │     │     → 1건이라도 실패 → Open (카운트 +1)
    │     │
    │     └── 응답 실패 → 대기 계속
    │
    ├── 10초 후: 재시도 (카운트 1이면)
    ├── 20초 후: 재시도 (카운트 2이면)
    ├── 40초 후: 재시도 (카운트 3이면)
    └── 60초 후: 재시도 (카운트 4+ → cap)
```

```
[상태 조회 CB — pgSimulator-status-realtime]

  CB Open
    │
    ▼
  Progressive Backoff (5s → 10s → 20s → 60s)
    │
    ▼
  Half-Open
    ├── 실제 조회 요청 2건 허용 (읽기 전용이라 안전)
    │     → 성공 → Closed
    │     → 실패 → Open (카운트 +1, 다음 대기 시간 증가)
```

### 15.5 구현 범위

| 전략 | 현재 과제 | 프로덕션 |
|------|----------|---------|
| Progressive Backoff | **구현** (이벤트 리스너 + 수동 전환) | 동적 설정 변경 |
| Health Check Probe | **구현** (request CB에 적용) | 전체 외부 시스템 확장 |
| Phased Ramp-up | 미적용 (Multi-PG가 보완) | 서비스 메시 레벨 적용 |

### 15.6 → 05 반영 사항

| 반영 대상 | 내용 |
|----------|------|
| Section 7 | Half-Open 전략 섹션 추가 (Progressive Backoff + Health Probe) |
| Section 16 | PgHealthChecker 클래스 추가 |
| Section 15 | Phase 2에 Health Probe 구현 항목 추가 |

---

## 16. 가주문/진주문 패턴 분석 (Provisional Order)

> **배경**: 고객이 주문서를 만들어도 결제까지 진행하지 않을 수 있다.
> 모든 주문서 생성을 DB에 저장하면 불필요한 데이터가 쌓인다.
> Redis에 '가주문'을 만들어두고, 결제 완료 시 '진주문'으로 전환하는 패턴을 검토한다.

### 16.1 패턴 개요

```
[현재 구조]
주문서 작성 → DB INSERT (Order CREATED) → 결제 요청 → 결제 완료 → PAID

[가주문/진주문 구조]
주문서 작성 → Redis SET (가주문) → 결제 요청 → 결제 완료 → DB INSERT (진주문 PAID)
                    │
                    └── TTL 만료 (30분) → 자동 삭제 (결제 미진행)
```

### 16.2 장점

| 항목 | 효과 |
|------|------|
| DB 부하 감소 | 결제 미완료 주문이 DB에 쌓이지 않음 |
| 쓰기 성능 | Redis SET은 DB INSERT 대비 10~100배 빠름 |
| 자동 정리 | TTL로 미결제 가주문 자동 만료, 별도 배치 불필요 |
| 조회 성능 | 진행 중 주문 조회가 Redis에서 즉시 응답 |

### 16.3 핵심 문제: 재고 차감 시점

#### Option A: 가주문 시점에 재고 차감 (Redis에서)

```
가주문 생성 → Redis DECR(stock) → 결제 요청 → 성공 → DB INSERT + DB 재고 확정
                                             → 실패 → Redis INCR(stock) 복원
```

- **장점**: 결제 중 재고 초과 판매(overselling) 방지
- **문제**: Redis 장애 시 재고 예약 정보 유실 → DB와 불일치
- **문제**: TTL 만료 시 재고 복원 로직 필요 (Keyspace Notification 또는 배치)

#### Option B: 진주문 시점에 재고 차감 (DB에서)

```
가주문 생성 → Redis SET (재고 미차감) → 결제 요청 → 성공 → DB INSERT + DB 재고 차감
```

- **장점**: 재고 정합성이 DB 트랜잭션으로 보장
- **문제**: 결제 진행 중 동일 상품에 다른 고객이 주문하면 재고 초과 판매 가능
- **문제**: 인기 상품(플래시 세일)에서 치명적

#### Option C: Redis 예약 + DB 확정 (이중 관리)

```
가주문 생성 → Redis DECR(stock) 예약 → 결제 요청 → 성공 → DB INSERT + DB 재고 차감
                                                  → 실패 → Redis INCR(stock) 복원

[배치] Redis 재고 ↔ DB 재고 정합성 주기 확인 (5분)
[배치] TTL 만료 가주문의 Redis 재고 미복원 건 감지 + 보정
```

- **장점**: 결제 중 overselling 방지 + DB 최종 정합성 보장
- **문제**: Redis-DB 이중 관리 복잡성
- **문제**: Redis 장애 → 재고 예약 불가 → 가주문 생성 불가 (Redis가 SPOF)

### 16.4 쿠팡 관점 트레이드오프 분석

#### 쿠팡에서 이 패턴이 적합한 상황

| 상황 | 적합도 | 이유 |
|------|--------|------|
| **장바구니 → 주문서** | ✅ 적합 | 전환율 낮음, DB 저장은 낭비 |
| **플래시 세일** | ✅ 매우 적합 | 초당 수만 건 주문 → DB 직접 쓰기 병목 |
| **일반 주문** | ⚠️ 과도할 수 있음 | 전환율 높으면 대부분 DB 저장 → Redis 경유 비용만 추가 |

#### 쿠팡에서 우려되는 점

| 우려 | 심각도 | 설명 |
|------|--------|------|
| **Redis SPOF** | 🔴 높음 | Redis 장애 = 주문 불가. DB만 있으면 최소한 느리게라도 주문 가능 |
| **재고 이중 관리** | 🔴 높음 | Redis 재고와 DB 재고 불일치 시 운영 혼란 |
| **장애 복구 복잡성** | 🟡 중간 | Redis 재시작 시 가주문 + 재고 예약 복원 필요 |
| **모니터링 난이도** | 🟡 중간 | 주문 데이터가 Redis/DB에 분산 → 통합 조회 어려움 |

### 16.5 현재 과제 적용 판단

```
[결론: 적용 — Redis 장애 대응까지 설계]

이유:
1. Resilience 과제의 본질은 "외부 시스템 장애에 대한 대응"
2. PG만 외부 시스템이 아니다 — Redis도 장애가 발생하는 외부 의존성
3. Redis 장애 시나리오 + Fallback 설계 = 과제의 학습 범위 확장
4. "장애 포인트가 늘어나니 안 쓴다"는 회피이지 대응이 아니다
5. 쿠팡 관점: Redis 없는 이커머스는 없다. 장애를 피할 수 없으면 대비해야 한다

적용 방식: Option C (Redis 예약 + DB 확정)
- 가주문: Redis Hash (TTL 30분)
- 재고 예약: Redis DECR (가주문 시) + DB UPDATE (진주문 시)
- Redis 장애 시: DB 직접 주문으로 Fallback
```

### 16.6 프로젝트 기존 인프라 확인 (중요)

> **이전 분석에서의 실수**: Redis를 "새로 추가할 외부 의존성"으로 판단하고
> 의존성 추가, docker-compose 생성, RedisConfig 작성 등을 설계했으나,
> 프로젝트에 **이미 모두 존재**하는 것으로 확인됨.

#### 이미 존재하는 것 — 건드릴 필요 없음

| 항목 | 위치 | 상세 |
|------|------|------|
| Redis 서버 (Master) | `docker/infra-compose.yml` | port 6379, AOF 영속성, healthcheck |
| Redis 서버 (Replica) | `docker/infra-compose.yml` | port 6380, 읽기 전용, Master 복제 |
| spring-boot-starter-data-redis | `modules/redis/build.gradle.kts` | 이미 포함 |
| RedisConfig (Master-Replica) | `modules/redis/.../RedisConfig.java` | LettuceConnectionFactory, Master/Replica 분리 |
| defaultRedisTemplate | RedisConfig | `ReadFrom.REPLICA_PREFERRED` (읽기 → Replica 우선) |
| masterRedisTemplate | RedisConfig (`@Qualifier("redisTemplateMaster")`) | `ReadFrom.MASTER` (쓰기 전용) |
| redis.yml (local 프로필) | `modules/redis/src/main/resources/` | master: localhost:6379, replica: localhost:6380 |
| Testcontainers | `modules/redis` testFixtures | `RedisTestContainersConfig`, `RedisCleanUp` |
| commerce-api 의존성 | `apps/commerce-api/build.gradle.kts` | `implementation(project(":modules:redis"))` 이미 선언 |

```
실행 방법: docker-compose -f ./docker/infra-compose.yml up
→ MySQL + Redis Master + Redis Replica + Kafka 전부 기동
```

#### 우리가 추가할 것 — 비즈니스 로직 + Resilience만

```
1. ProvisionalOrderRedisRepository  → masterRedisTemplate으로 쓰기 (가주문 생성, 재고 DECR)
2. 가주문 조회                      → defaultRedisTemplate으로 읽기 (Replica 우선)
3. Resilience4j CB/Timeout          → Redis 호출에 CB 적용
4. Fallback                        → Redis CB Open → DB 직접 주문
5. 재고 정합성 배치                   → StockReconcileScheduler
```

### 16.7 Redis Resilience 설계

#### 16.7.1 Master-Replica를 활용한 장애 분리

기존 `RedisConfig`가 이미 Master/Replica를 분리하고 있으므로,
쓰기 장애와 읽기 장애를 **독립적으로** 대응할 수 있다.

```
[쓰기 — masterRedisTemplate (Master only)]
가주문 생성 (HSET) + 재고 예약 (DECR)
→ Master 장애 시: CB Open → DB 직접 주문 Fallback

[읽기 — defaultRedisTemplate (Replica preferred)]
가주문 조회 (HGETALL)
→ Replica 우선 → Master fallback (Lettuce 자동)
→ 둘 다 죽으면: CB Open → DB 조회 Fallback

핵심: Master가 죽어도 Replica에서 읽기는 가능
→ 결제 진행 중인 가주문 조회는 Master 장애에 영향 받지 않음
```

#### 16.7.2 Redis가 사용되는 지점

| 기능 | Redis 명령 | 사용 Template | 실패 시 영향 |
|------|-----------|-------------|-------------|
| 가주문 생성 | `HSET provisional:order:{orderId}` | `masterRedisTemplate` | 주문 불가 → DB Fallback |
| 가주문 조회 | `HGETALL provisional:order:{orderId}` | `defaultRedisTemplate` | Replica에서 읽기 시도 |
| 재고 예약 | `DECR stock:{productId}` | `masterRedisTemplate` | 주문 불가 → DB Fallback |
| 재고 복원 | `INCR stock:{productId}` | `masterRedisTemplate` | 정합성 배치가 보정 |
| 가주문 삭제 | `DEL provisional:order:{orderId}` | `masterRedisTemplate` | TTL이 보완 |

#### 16.7.3 Redis Timeout 설정

```
기존 modules/redis의 LettuceClientConfiguration에 Timeout 추가 필요.
현재 RedisConfig에는 타임아웃 설정이 없으므로, Lettuce 레벨에서 설정한다.
```

```java
// modules/redis/RedisConfig.java에 타임아웃 추가
LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
    .commandTimeout(Duration.ofMillis(500))     // 커맨드 타임아웃
    .shutdownTimeout(Duration.ofMillis(200))
    .readFrom(readFrom)
    .build();
```

```
타임아웃 근거:
- Redis 단일 명령은 보통 1ms 이내 응답
- 500ms command timeout = 정상 대비 500배 마진
- PG 호출(500ms connect + 1000ms read = 1500ms)보다 충분히 빠르게 실패해야 함
- Redis가 느리면 PG보다 먼저 Fallback 판단해야 함
```

#### 16.7.4 Redis Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      # --- Redis 쓰기 (Master) ---
      redis-write:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s    # Redis는 복구가 빠르므로 짧게
        permitted-number-of-calls-in-half-open-state: 3
        record-exceptions:
          - org.springframework.data.redis.RedisConnectionFailureException
          - io.lettuce.core.RedisCommandTimeoutException
          - org.springframework.data.redis.RedisSystemException

      # --- Redis 읽기 (Replica preferred) ---
      redis-read:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 3s    # 읽기는 더 빠르게 재시도
        permitted-number-of-calls-in-half-open-state: 3
```

```
CB 설계 근거:
- Redis 쓰기/읽기 CB를 분리 → Master 장애가 읽기를 차단하지 않음
  (PG에서 request/status CB를 분리한 것과 동일한 원칙)
- Redis는 PG보다 복구가 빠름 (재시작 수초, Replica→Master 승격 10~30초)
- wait-duration 짧게: 빠르게 Half-Open 시도
- failure-rate 50%: Redis가 10건 중 5건 실패하면 이미 심각한 장애
```

#### 16.7.5 Redis 장애 시나리오 + 대응

| 시나리오 | 현상 | 영향 범위 | 대응 |
|---------|------|----------|------|
| **R1: Master 연결 실패** | ConnectException | 쓰기 불가 | `redis-write` CB Open → DB Fallback |
| **R2: Master 응답 지연** | CommandTimeout (500ms 초과) | 쓰기 지연 | Timeout → CB 기록 → 누적 시 Open |
| **R3: Master 메모리 초과** | OOM 에러 | 쓰기 거부 | CB Open → DB Fallback |
| **R4: Master-Replica 전환** | 복제 끊김, 재연결 | 일시적 쓰기 실패 | CB Open → 5초 후 Half-Open |
| **R5: Master + Replica 모두 다운** | 전체 Redis 장애 | 읽기+쓰기 불가 | 양쪽 CB Open → DB Fallback |
| **R6: Replica만 다운** | Replica 응답 없음 | 없음 | Lettuce가 자동으로 Master에서 읽기 |
| **R7: Redis 데이터 유실** | 재시작 후 가주문 소멸 | 가주문 미조회 | 사용자에게 재주문 안내 |
| **R8: Redis-DB 재고 불일치** | Redis 복원 실패 | 재고 수치 오차 | 정합성 배치가 DB 기준으로 보정 |

### 16.8 SOT(Source of Truth) 설계 — 데이터 정합성의 기준

#### 16.8.1 핵심 원칙: SOT는 단계별로 전환된다

```
"Redis Master가 SOT다" 또는 "DB가 SOT다"가 아니다.
데이터의 생명주기에 따라 SOT가 전환된다.
```

| 단계 | SOT | 데이터 위치 | 상태 |
|------|-----|-----------|------|
| **가주문 생성** | Redis Master | Redis에만 존재 | 임시 (TTL 30분) |
| **결제 진행 중** | Redis Master | Redis (가주문) | PG 응답 대기 |
| **결제 완료 → 진주문 전환** | Redis → DB | DB INSERT + Redis DEL | **SOT 이전 시점** |
| **진주문 이후** | DB (MySQL) | DB에만 존재 | 영구 |

```
시간축:

  주문서 작성          결제 완료              이후
      │                   │                   │
      ▼                   ▼                   ▼
  [Redis Master = SOT]  [SOT 이전]          [DB = SOT]
  가주문 HSET           DB INSERT(진주문)    DB만 정본
  재고 DECR(예약)       DB 재고 차감(확정)   Redis에 데이터 없음
                        Redis DEL(가주문)
```

#### 16.8.2 SOT 전환 시 정합성 위험 3가지

##### (1) SOT 전환 실패: DB INSERT 성공 + Redis DEL 실패

```
결제 완료
  → DB INSERT(진주문) ✅
  → DB 재고 차감 ✅
  → Redis DEL(가주문) ❌  ← Master 일시 장애

결과: SOT가 DB와 Redis에 동시 존재
  - DB: 진주문 있음, 재고 차감됨
  - Redis: 가주문 남아있음, 재고 예약도 남아있음
  - 재고가 이중으로 잡혀 있는 상태 (DB 차감 + Redis 예약)
```

| 대응 | 내용 |
|------|------|
| **즉시** | Redis DEL 실패는 로그 + 모니터링 (즉시 재시도 1회) |
| **TTL 보완** | 가주문 TTL 30분 → 최대 30분 후 자동 정리 |
| **정합성 배치** | 5분 주기: DB에 진주문이 있는데 Redis에도 가주문이 남아있는 건 → Redis DEL |
| **재고 배치** | 5분 주기: DB 재고를 기준으로 Redis 재고 보정 |

##### (2) Redis Master 장애로 가주문 유실

```
가주문 생성 (Master HSET ✅, 비동기 복제 아직 안 됨) → Master 장애

결과: SOT 자체가 사라짐
  - Redis: 데이터 없음 (Master 유실, Replica 복제 안 됨)
  - DB: 데이터 없음 (가주문은 DB에 저장하지 않았음)
  - 고객은 주문서를 만들었다고 생각하지만, 시스템에 기록 없음
```

| 대응 | 내용 |
|------|------|
| **사용자 안내** | "주문 정보가 만료되었습니다. 다시 주문해주세요" |
| **금전 손실** | 없음 — 결제가 진행되지 않았으므로 |
| **비즈니스 영향** | 가주문은 임시 데이터 → 유실이 크리티컬하지 않음 |
| **설계 근거** | 가주문을 DB에도 저장하면 성능 이점이 사라짐 → 유실 허용이 올바른 트레이드오프 |

##### (3) Replica 비동기 지연: Write-then-Read 불일치

```
고객: 가주문 생성 요청
  → masterRedisTemplate.HSET(가주문) ✅  →  (비동기 복제 시작)

고객: 방금 만든 가주문 조회 요청 (100ms 후)
  → defaultRedisTemplate.HGETALL(가주문) → Replica에서 읽기
  → Replica에 아직 복제 안 됨 → 없음!
  → "방금 만든 주문이 안 보여요"
```

| 대응 | 내용 |
|------|------|
| **원칙** | **Write 직후 Read는 반드시 Master에서 읽는다** |
| **구현** | 가주문 생성 직후 응답에 가주문 정보를 포함하여 반환 (추가 조회 불필요) |
| **일반 조회** | 목록/상태 확인 등은 `defaultRedisTemplate` (Replica 우선) OK — sub-ms 지연은 무시 가능 |

```java
// ❌ 잘못된 패턴: Write → 즉시 Replica에서 Read
masterRedisTemplate.opsForHash().putAll("provisional:order:" + orderId, data);
defaultRedisTemplate.opsForHash().entries("provisional:order:" + orderId); // Replica → 없을 수 있음

// ✅ 올바른 패턴 1: Write → Master에서 Read
masterRedisTemplate.opsForHash().putAll("provisional:order:" + orderId, data);
masterRedisTemplate.opsForHash().entries("provisional:order:" + orderId); // Master → 항상 있음

// ✅ 올바른 패턴 2: Write 응답에 데이터 포함 (추가 Read 불필요)
ProvisionalOrderResult result = ProvisionalOrderResult.provisional(orderId, data);
return result; // 조회 없이 생성 시점의 데이터를 그대로 반환
```

#### 16.8.3 SOT 설계 원칙 정리

```
1. DB가 최종 SOT (Source of Truth)
   - 진주문, 결제, 재고의 최종 정본은 항상 DB
   - 모든 정합성 보정은 DB 기준으로 수행

2. Redis는 임시 SOT
   - 가주문, 재고 예약은 Redis Master가 SOT
   - 임시 데이터이므로 유실 허용 (금전 손실 없는 범위)

3. SOT 이전은 원자적이어야 한다
   - DB INSERT(진주문) + DB 재고 차감 = 하나의 트랜잭션
   - Redis DEL(가주문)은 트랜잭션 밖 → 실패 허용 (TTL + 배치가 보정)

4. Write-then-Read는 SOT에서 읽는다
   - 가주문 생성 직후 조회 → Master에서 읽기
   - 일반 조회 → Replica 우선 (지연 허용)

5. 정합성 배치는 항상 DB → Redis 방향
   - Redis → DB 보정은 없음 (Redis는 임시 데이터)
   - DB 기준으로 Redis를 맞춘다
```

---

### 16.9 Redis Fallback: DB 직접 주문 (Degraded Mode)

#### 핵심 원칙

> Redis가 죽어도 주문은 받아야 한다.
> 가주문 패턴은 **성능 최적화**이지 **필수 경로**가 아니다.
> Redis 장애 시 DB 직접 주문으로 떨어지면 느리지만 동작한다.

#### Fallback 흐름

```
[정상 경로 — Redis 사용]
주문 요청 → redis-write CB Closed
  → masterRedisTemplate.DECR(재고) + HSET(가주문, TTL 30m)
  → 결제 요청
  → 성공 → DB INSERT(진주문) + DB 재고 차감 + masterRedisTemplate.DEL(가주문)

[Fallback 경로 — DB 직접]
주문 요청 → redis-write CB Open
  → DB INSERT(Order CREATED) + DB 재고 차감 (기존 로직 그대로)
  → 결제 요청
  → 성공 → DB UPDATE(PAID)
```

```java
@Component
public class ProvisionalOrderService {

    @Qualifier("redisTemplateMaster")
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;

    /**
     * Redis CB Open 시 DB 직접 주문으로 Fallback
     */
    @CircuitBreaker(name = "redis-write", fallbackMethod = "createOrderViaDatabaseFallback")
    public ProvisionalOrderResult createProvisionalOrder(OrderCreateRequest request) {
        // Redis 경로: 가주문 생성 + 재고 예약 (masterRedisTemplate 사용)
        masterRedisTemplate.opsForHash().putAll(
            "provisional:order:" + request.getOrderId(),
            request.toMap()
        );
        masterRedisTemplate.expire(
            "provisional:order:" + request.getOrderId(),
            Duration.ofMinutes(30)
        );
        masterRedisTemplate.opsForValue().decrement("stock:" + request.getProductId());

        return ProvisionalOrderResult.provisional(request.getOrderId());
    }

    /**
     * Fallback: DB 직접 주문 (현재 구조와 동일)
     */
    public ProvisionalOrderResult createOrderViaDatabaseFallback(
            OrderCreateRequest request, Exception e) {
        log.warn("Redis 장애 — DB 직접 주문으로 Fallback. orderId={}", request.getOrderId(), e);

        Order order = Order.create(request);
        orderRepository.save(order);
        stockRepository.decrease(request.getProductId(), request.getQuantity());

        return ProvisionalOrderResult.directOrder(order.getId());
    }
}
```

#### Fallback 시 달라지는 점

| 항목 | 정상 (Redis) | Fallback (DB) |
|------|-------------|---------------|
| 주문 저장 위치 | Redis (TTL 30분) | DB (영구) |
| 재고 차감 | Redis DECR (빠름) | DB UPDATE + 비관적 락 (느림) |
| 미결제 정리 | TTL 자동 만료 | 배치로 CREATED → CANCELLED 전환 |
| 쓰기 성능 | ~1ms | ~10~50ms |
| 동시성 처리 | Redis 단일 스레드 (원자적) | DB 락 경합 가능 |
| 읽기 | Replica에서 조회 (Master 장애 무관) | DB 조회 |

### 16.10 Redis-DB 재고 정합성 배치

```java
/**
 * 5분 주기: Redis 재고와 DB 재고 비교 + 불일치 보정
 *
 * 불일치 원인:
 * - Redis 재시작으로 DECR 이력 유실
 * - 가주문 TTL 만료 시 INCR 누락
 * - Fallback 모드에서 DB만 차감하고 Redis 미반영
 */
@Scheduled(fixedRate = 300_000) // 5분
public void reconcileStock() {
    List<Product> products = productRepository.findAllActive();
    for (Product product : products) {
        // 읽기: defaultRedisTemplate (Replica 우선) — 보정 판단은 읽기로 충분
        String redisStockStr = defaultRedisTemplate.opsForValue().get("stock:" + product.getId());
        Integer redisStock = redisStockStr != null ? Integer.parseInt(redisStockStr) : null;
        int dbStock = product.getStock().getValue();

        if (redisStock == null || Math.abs(redisStock - dbStock) > 0) {
            log.warn("재고 불일치 감지: productId={}, redis={}, db={}",
                product.getId(), redisStock, dbStock);
            // 쓰기: masterRedisTemplate (Master) — DB 기준으로 Redis 보정
            masterRedisTemplate.opsForValue().set("stock:" + product.getId(), String.valueOf(dbStock));
        }
    }
}
```

```
정합성 원칙:
- DB가 항상 정본 (Source of Truth)
- Redis는 성능 캐시 + 임시 저장소
- 불일치 시 DB 기준으로 Redis를 보정
- 보정 이력은 로그로 남김 (운영 추적용)
```

### 16.11 가주문/진주문 전체 아키텍처

```
[정상 흐름]
Client → API → redis-write CB Closed?
                 ├── Yes → masterRedisTemplate.DECR(stock) + HSET(가주문, TTL 30m)
                 │           → PG 결제 요청
                 │           → 콜백/폴링으로 결과 수신
                 │           → 성공: DB INSERT(진주문) + DB 재고 차감 + masterRedisTemplate.DEL(가주문)
                 │           → 실패: masterRedisTemplate.INCR(stock) + DEL(가주문)
                 │
                 └── No (CB Open) → DB INSERT(Order CREATED) + DB 재고 차감
                                    → PG 결제 요청 (기존 로직과 동일)
                                    → 콜백/폴링으로 결과 수신
                                    → 성공: DB UPDATE(PAID)
                                    → 실패: DB 재고 복원 + DB UPDATE(CANCELLED)

[가주문 조회 — Replica 우선]
Client → API → defaultRedisTemplate.HGETALL(가주문)
                 → Replica 응답 → 성공
                 → Replica 다운 → Lettuce가 자동으로 Master에서 읽기
                 → 모두 다운 → redis-read CB Open → DB 조회 Fallback

[배치]
- 5분 주기: Redis ↔ DB 재고 정합성 보정 (DB가 Source of Truth)
- 30분 주기: DB에서 CREATED 상태 30분 초과 건 → CANCELLED 전환 (Fallback 모드 잔여분)
```

### 16.12 아키텍처 교훈

> **1차 판단**: "외부 의존성을 추가하면 장애 포인트가 늘어나니 안 쓴다" — **회피**
> **2차 판단**: "외부 의존성을 추가하고, Resilience도 함께 설계한다" — **대응**
> **3차 판단 (현재)**: "이미 있는 인프라를 확인하지 않고 '추가' 논의를 한 것 자체가 실수" — **확인 우선**
>
> **교훈**: 설계 전에 기존 인프라를 반드시 확인한다.
> 이 프로젝트에서 Redis는 "추가할 것"이 아니라 "이미 Master-Replica까지 구성된 인프라"였다.
> `modules/redis`, `docker/infra-compose.yml`을 먼저 확인했으면 불필요한 설계 시간을 줄일 수 있었다.

### 16.13 → 05 반영 사항

| 반영 대상 | 내용 |
|----------|------|
| Section 7 | Redis CB 2개 (`redis-write`, `redis-read`) 설정 추가 |
| Section 8 | Redis Fallback (DB 직접 주문) 추가 |
| Section 15 | Phase 1에 가주문 모델, Phase 3에 Redis Resilience + 정합성 배치 |
| Section 16 | Redis 관련 패키지 구조 추가 (RedisConfig는 이미 존재하므로 제외) |
| ~~의존성~~ | ~~spring-boot-starter-data-redis 추가~~ → **이미 존재, 불필요** |
| 신규 | SOT 전환 원칙 + Write-then-Read 패턴 → Master 읽기 원칙 |

### 16.14 정합성 갭 분석 + 대응 방안

> 가주문/진주문 패턴에서 TTL, 배치 주기, SOT 전환 사이에 정합성 갭이 발생하는
> 모든 지점을 식별하고, 산술적 근거에 기반한 대응 방안을 설계한다.

#### 16.14.1 갭 전수 목록

| # | 갭 발생 지점 | 갭 시간 | 핵심 위험 | 심각도 |
|---|------------|---------|---------|--------|
| G1 | Replica 비동기 복제 지연 | < 1ms | Write-then-Read 불일치 | 🟢 |
| G2 | Outbox 폴러 주기 | 최대 5초 | 결제 요청 지연 | 🟢 |
| G3 | 배치 복구 주기 | 최대 1분 | 결제 상태 미확정 | 🟡 |
| G4 | PENDING 최대 대기 | 최대 5분 | 고객 체감 지연 | 🟡 |
| G5 | 재고 정합성 배치 + 가주문 미고려 | 최대 5분 | **overselling** | 🔴 |
| G6 | SOT 전환 실패 (Redis DEL 실패) | 5분~30분 | 재고 이중 차감 | 🟡 |
| G7 | 가주문 TTL 만료 + 재고 미복원 | 최대 30분 | **재고 영구 감소 누적** | 🔴 |
| G8 | Fallback 모드 미결제 정리 | 최대 30분 | 재고 묶임 | 🟡 |

#### 16.14.2 배치 주기의 산술적 근거

##### 현재 과제 규모

```
상품 수: ~100개 (활성 상품)
주문량: ~10건/분 (테스트 환경)
가주문 동시 존재: ~5건 (10건/분 × 30% 미결제 × 30분 TTL → 실제로는 소수)
```

##### 재고 정합성 배치 1회 비용

```
[Redis 읽기] 100개 상품 재고 GET
  → 100 × 0.1ms = 10ms (pipelining 시 2ms)

[DB 읽기] 상품 재고 조회 (1 query)
  → SELECT id, stock FROM product WHERE active = true
  → ~5ms

[Redis 가주문 스캔] 진행 중 가주문 수 파악
  → SCAN pattern "provisional:order:*"
  → ~5건 → < 1ms

[Redis 보정 쓰기] 불일치 건 SET
  → 평균 2~3건 × 0.1ms = < 1ms

총 비용: ~15ms / 회
```

##### 배치 주기별 시스템 부하율

| 주기 | 부하율 (15ms / 주기) | 판단 |
|------|---------------------|------|
| 5분 (300초) | 15ms / 300s = **0.005%** | 과도하게 여유로움 |
| 1분 (60초) | 15ms / 60s = **0.025%** | 여유로움 |
| 10초 | 15ms / 10s = **0.15%** | 충분히 가능 |
| 5초 | 15ms / 5s = **0.3%** | 가능 |
| 1초 | 15ms / 1s = **1.5%** | 가능하지만 불필요 |

```
결론: 현재 과제 규모에서 배치 주기 5분은 과도하게 느슨하다.
→ 10초~30초 주기로 변경 가능 (시스템 부하 1% 미만)
```

##### 쿠팡 규모 시뮬레이션

```
상품 수: 100,000개
주문량: 10,000건/분 (피크)
가주문 동시 존재: ~90,000건 (10,000/분 × 30% × 30분)

배치 1회 비용:
  [Redis] 100,000 GET (pipelining 100배치): ~100ms
  [DB]    1 query (인덱스 사용): ~200ms
  [SCAN]  90,000건 패턴 스캔: ~900ms
  [보정]  ~1,000건 SET: ~100ms
  총 비용: ~1,300ms / 회

주기별 부하율:
  5분: 1.3s / 300s = 0.43%     → 충분
  1분: 1.3s / 60s  = 2.2%      → 허용 가능
  30초: 1.3s / 30s = 4.3%      → 허용 가능
  10초: 1.3s / 10s = 13%       → 피크 시 부담
  5초: 1.3s / 5s   = 26%       → 위험
```

```
쿠팡 규모 결론:
- 30초~1분 주기가 최적 (부하 2~4%)
- SCAN이 병목 → 가주문 키를 별도 SET으로 관리하면 SCAN 제거 가능
  (SADD "provisional:orders" orderId → SMEMBERS로 O(1) 조회)

현재 과제:
- 10초~30초 주기 적용 (부하 0.15% 이하)
- 30분은 과도하게 느슨 → 재고 묶임 시간 불필요하게 김
```

#### 16.14.3 G7 대응: 가주문 TTL 만료 시 재고 미복원 문제

##### 문제 재정의

```
Redis TTL 만료 = Key 삭제만 실행. 연관 로직(재고 INCR)은 실행되지 않는다.

가주문 생성: HSET + DECR(stock)
30분 후 TTL 만료: HSET 삭제 ✅ / INCR(stock) ❌
→ 재고가 영구적으로 감소된 상태로 남음
```

##### 대응: 가주문 만료 감지 배치 (Proactive Expiry Scanner)

```
[기존 설계] TTL 만료를 기다림 → 30분 후 Key 사라짐 → 재고 미복원
[개선 설계] TTL 만료 전에 선제적으로 감지 → 재고 복원 + 가주문 삭제
```

```java
/**
 * 가주문 만료 감지 배치 — 30초 주기
 *
 * TTL이 30초 미만인 가주문을 선제적으로 정리:
 * 1. 가주문의 TTL 잔여 시간 확인
 * 2. TTL < 30초 → 결제 미진행으로 판단
 * 3. 재고 INCR(복원) + 가주문 DEL
 *
 * 이렇게 하면 TTL 만료 시점에는 이미 정리 완료 → 재고 미복원 문제 없음
 */
@Scheduled(fixedRate = 30_000) // 30초
public void cleanupExpiringProvisionalOrders() {
    // provisional:orders SET에서 모든 가주문 ID 조회 (SCAN 대신 SMEMBERS)
    Set<String> orderIds = masterRedisTemplate.opsForSet()
        .members("provisional:orders");

    if (orderIds == null) return;

    for (String orderId : orderIds) {
        String key = "provisional:order:" + orderId;
        Long ttl = masterRedisTemplate.getExpire(key, TimeUnit.SECONDS);

        if (ttl == null || ttl < 0) {
            // 이미 만료됨 — SET에서만 제거
            masterRedisTemplate.opsForSet().remove("provisional:orders", orderId);
            continue;
        }

        if (ttl < 30) {
            // TTL 30초 미만 — 결제 미진행으로 판단, 선제 정리
            String productId = (String) masterRedisTemplate.opsForHash()
                .get(key, "productId");
            String quantity = (String) masterRedisTemplate.opsForHash()
                .get(key, "quantity");

            // 재고 복원
            masterRedisTemplate.opsForValue()
                .increment("stock:" + productId, Long.parseLong(quantity));

            // 가주문 삭제 + SET에서 제거
            masterRedisTemplate.delete(key);
            masterRedisTemplate.opsForSet().remove("provisional:orders", orderId);

            log.info("가주문 선제 정리: orderId={}, productId={}, 재고 +{}",
                orderId, productId, quantity);
        }
    }
}
```

```
가주문 생성 시:
  masterRedisTemplate.opsForSet().add("provisional:orders", orderId);
  masterRedisTemplate.expire("provisional:order:" + orderId, jitteredTtl());
  // jitteredTtl(): 25분~35분 (±5분 Jitter 적용)

이점:
- SCAN 불필요 → SMEMBERS로 O(1) 조회
- TTL 만료 전에 정리 → 재고 미복원 갭 0으로 감소
- 30초 주기 → 최대 갭 60초 (기존 30분에서 30배 단축)
- TTL Jitter로 동시 만료 분산 → 배치 1회 피크 부하 24배 감소
```

##### 배치 주기 30초의 근거

```
가주문 TTL: 30분 = 1800초
배치 주기: 30초
TTL 감지 기준: 잔여 30초 미만

→ 배치가 30초마다 실행, TTL 30초 미만 감지
→ 최악의 경우: 배치 직후 TTL이 30초가 된 건 → 다음 배치(30초 후)에서 감지
→ 최대 갭: 30초 + 30초 = 60초 (TTL 만료 시점 기준)
→ 실제로는 대부분 30초 이내에 감지

비용 (현재 과제):
  SMEMBERS ~5건: < 1ms
  TTL 확인 5건: 5 × 0.1ms = 0.5ms
  총 비용: ~2ms / 회
  부하율: 2ms / 30s = 0.007%
```

#### 16.14.4 TTL Jitter — 동시 만료 방지

##### 문제

```
가주문 TTL이 모두 30분으로 동일하면:

플래시 세일 12:00:00 시작 → 10초 내 1000건 주문
→ 12:30:00~12:30:10 사이에 1000건 동시 만료
→ Proactive Expiry Scanner에 1000건이 한 번에 걸림
→ 배치 1회 처리 부하 급증

비용 계산 (Proactive Expiry Scanner):
  SMEMBERS: ~1ms
  TTL 확인 1000건: 1000 × 0.1ms = 100ms
  INCR + DEL + SREM (만료 건): 최대 1000건 × 0.5ms = 500ms
  → 배치 1회 비용: ~601ms (평상시 ~2ms 대비 300배)
```

##### 대응: TTL에 ±5분 Jitter 적용

```
Base TTL: 30분 (1800초)
Jitter 범위: ±5분 (±300초)
실제 TTL: 25분 ~ 35분 (1500초 ~ 2100초)

1000건 주문이 10초 이내에 몰려도:
  만료 시점 분포: 12:25:00 ~ 12:35:00 (10분 범위)
  → 분당 평균 ~100건 만료
  → 배치(30초) 1회당 ~50건 처리
  → 배치 1회 비용: ~25ms (601ms 대비 24배 감소)
```

##### Jitter 적용 코드

```java
private static final long BASE_TTL_SECONDS = 1800;  // 30분
private static final long JITTER_RANGE = 300;        // ±5분

private Duration jitteredTtl() {
    long jitter = ThreadLocalRandom.current().nextLong(-JITTER_RANGE, JITTER_RANGE + 1);
    return Duration.ofSeconds(BASE_TTL_SECONDS + jitter);
}

// 가주문 생성 시
masterRedisTemplate.expire("provisional:order:" + orderId, jitteredTtl());
```

##### Jitter + Proactive Expiry Scanner 조합 효과

```
Jitter 없이 Proactive Scanner만:
  - 동시 만료 시점에 배치 1회 비용 급증 (601ms)
  - 배치 주기(30초) 내에 처리 완료되므로 "동작은 함"
  - 그러나 Redis 순간 부하 + 스케줄러 지연 가능

Jitter만 적용 (Scanner 없이):
  - 만료 시점 분산되지만, TTL 만료 = Key 삭제만 → 재고 미복원 문제 여전
  - G7 문제 자체를 해결하지 못함

Jitter + Proactive Scanner 조합:
  - Scanner가 G7을 해결 (선제 정리 → 재고 복원)
  - Jitter가 Scanner의 부하를 분산 (피크 24배 감소)
  → 서로 다른 문제를 해결하는 보완 관계

결론: 둘 다 적용. Jitter는 보험, Scanner는 핵심.
```

##### Jitter 범위 ±5분의 근거

```
가주문 유효 기간 관점:
  최소 TTL: 25분 → 결제 완료에 충분한 시간 (PG 비동기 처리 최대 5초 + 여유)
  최대 TTL: 35분 → 기존 30분 대비 5분 연장. 재고 점유 약간 증가

재고 점유 영향:
  평균 TTL은 여전히 30분 (균등 분포)
  → 평균 재고 점유 시간 변화 없음
  최대 5분 추가 점유 → 상품당 1건 기준, 5분간 1개 추가 점유
  → 실질적 영향 무시 가능

Jitter 비율:
  ±5분 / 30분 = ±16.7%
  → 일반적 Jitter 권장 범위 (10~30%) 내
```

---

#### 16.14.5 G5 대응: 재고 정합성 배치의 Lost Update 문제

##### 문제 재정의

```
현재 설계: DB 재고를 읽어서 Redis에 SET (덮어쓰기)

배치 스레드:                     요청 스레드:
  1. DB 재고 조회: 10개
                                 2. DECR stock:123 → 9개 (가주문 생성)
  3. SET stock:123 10
                                 → Redis 재고가 10으로 복구됨
                                 → DECR이 사라짐! (Lost Update)
                                 → overselling 가능
```

##### 대안 비교: SET vs DELETE(evict) vs Lua

###### 방안 1: SET (현재 설계) — Lost Update 위험

```
배치: SET stock:123 <DB값>

문제: SET은 절대값 덮어쓰기
→ 배치 읽기 ~ SET 사이에 DECR이 끼면 Lost Update
```

###### 방안 2: DELETE (evict) — Stampede 위험

```
배치: DEL stock:123
→ 다음 읽기 시 cache miss → DB에서 로드 → SET

문제 1: Cache Stampede
  DEL 직후 100명이 동시 조회
  → 100개 cache miss → 100개 DB 조회 → 동일 쿼리 폭주

문제 2: DECR 실패
  DEL 직후 DECR stock:123 → key 없음 → 에러
  → 가주문 생성 실패
  → "재고 확인 중" 에러 응답 → 고객 이탈
```

```
⚠️ 스탬피드 정리:

스탬피드는 DELETE(evict) 시 발생하는 문제다. SET 시에는 발생하지 않는다.

- DELETE → key 사라짐 → 동시 다발 cache miss → DB 폭주 = 스탬피드
- SET → key 덮어씀 → 읽기 요청은 항상 hit → 스탬피드 없음

SET의 문제는 스탬피드가 아니라 Lost Update(동시 DECR 유실)다.
```

###### 방안 3: SET + DELETE 조합 — 문제 분리

```
의도: SET으로 재고 보정 + DELETE로 만료 가주문 정리

배치:
  1. 만료 가주문 정리: INCR(재고 복원) + DEL(가주문) ← 가주문에 대한 DELETE
  2. 재고 보정: SET stock:123 <보정값>      ← 재고에 대한 SET

→ 가주문 DELETE와 재고 SET은 대상이 다른 키
→ 가주문 키(provisional:order:*)에 DELETE → 스탬피드 무관 (캐시용이 아님)
→ 재고 키(stock:*)에 SET → Lost Update 위험은 여전히 존재
```

###### 방안 4: Lua Script — 원자적 보정 (권장)

```lua
-- stock_reconcile.lua
-- 원자적으로: 현재값 확인 → 진행 중 가주문 반영 → 보정

local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local dbStock = tonumber(ARGV[1])
local activeOrders = tonumber(ARGV[2])
local expected = dbStock - activeOrders

if current ~= expected then
    redis.call('SET', KEYS[1], tostring(expected))
    return expected  -- 보정됨
end
return -1  -- 보정 불필요
```

```
Lua가 해결하는 이유:

Redis는 Lua 스크립트를 단일 스레드에서 원자적으로 실행한다.

배치 스레드:                     요청 스레드:
  1. DB 재고 조회: 10개
  2. 가주문 수 조회: 3건
                                 3. DECR stock:123 → 이 시점에서 6개
  4. Lua 실행 (원자적):
     GET stock:123 → 6 (DECR 반영된 값)
     expected = 10 - 3 = 7
     6 ≠ 7 → SET stock:123 7
                                 → Redis 재고 7 (정확!)

Lua 실행 중에는 다른 명령(DECR 포함)이 끼어들 수 없다.
→ Lost Update 불가능
```

```
그런데 완벽하지는 않다:

DB 조회(step 1) ~ Lua 실행(step 4) 사이에 가주문이 추가/삭제될 수 있음.
→ step 2의 가주문 수가 step 4 시점과 다를 수 있음.

이 갭을 줄이려면:
- Lua 안에서 SMEMBERS로 가주문 수를 직접 세기 (DB 조회는 밖에서)
- 가주문 수 조회와 SET이 하나의 Lua에서 원자적으로 실행됨
```

```lua
-- stock_reconcile_v2.lua
-- 가주문 수도 Redis 안에서 원자적으로 조회

local stockKey = KEYS[1]                    -- stock:123
local provisionalSetKey = KEYS[2]            -- provisional:orders:123 (상품별)
local dbStock = tonumber(ARGV[1])            -- DB 재고

local current = tonumber(redis.call('GET', stockKey) or '0')
local activeCount = redis.call('SCARD', provisionalSetKey)  -- 진행 중 가주문 수
local expected = dbStock - activeCount

if current ~= expected then
    redis.call('SET', stockKey, tostring(expected))
    return expected
end
return -1
```

```
v2가 해결하는 것:
- GET(현재 재고) + SCARD(가주문 수) + SET(보정) 이 원자적
- DECR/INCR이 끼어들 수 없음
- DB 재고만 외부에서 조회하고, 나머지는 Lua 안에서 처리

남은 갭:
- DB 조회 ~ Lua 실행 사이에 DB 재고가 바뀔 수 있음
  (진주문 전환 등으로 DB UPDATE 발생)
- 그러나 이 갭은 수~수십 ms이며, 다음 배치(30초)에서 보정됨
- 실무에서 이 정도 갭은 허용 가능
```

#### 16.14.6 최종 대응 방안 요약

| 갭 | 기존 | 개선 | 개선 후 갭 |
|-----|------|------|----------|
| **G7: 가주문 만료 + 재고 미복원** | TTL 만료에 의존 (30분) | 선제 만료 배치 30초 주기 + TTL Jitter ±5분 | **최대 60초** (피크 부하 24배 감소) |
| **G5: 재고 배치 Lost Update** | SET 덮어쓰기 (Lost Update 위험) | Lua Script 원자적 보정 (v2) | **0** (원자적) |
| **G6: SOT 이중 존재** | TTL 30분 의존 | 정합성 배치에서 진주문 존재 여부 확인 + DEL | **최대 30초** (배치 주기) |

#### 16.14.7 개선된 배치 구조

```
[1] 가주문 선제 만료 배치 — 30초 주기
    → SMEMBERS provisional:orders
    → TTL < 30초인 건: INCR(재고 복원) + DEL(가주문) + SREM
    → TTL = -2 (이미 만료): SREM만

[2] 재고 정합성 배치 — 30초 주기 (Lua Script v2)
    → DB 재고 조회
    → 상품별 Lua 실행: GET(현재) + SCARD(가주문 수) + SET(보정)
    → 원자적 → Lost Update 없음

[3] SOT 정합성 배치 — 30초 주기
    → Redis 가주문 중 DB에 진주문(PAID)이 존재하는 건 감지
    → Redis DEL(가주문) + INCR(재고 복원) + SREM

[1][2][3]을 하나의 스케줄러에서 순차 실행 가능:
    총 비용 ~20ms / 30초 = 부하율 0.07%
```

### 16.15 → 05 반영 사항 (추가)

| 반영 대상 | 내용 |
|----------|------|
| Section 10 | 배치 복구 주기: 재고 정합성 5분 → 30초, Lua Script 적용 |
| Section 13 | 가주문 선제 만료 배치 추가 |
| Section 15 | Phase 1에 TTL Jitter 적용, Phase 3에 Lua Script 구현 + 가주문 만료 배치 항목 추가 |
| Section 16 | StockReconcileLuaScript, ProvisionalOrderExpiryScheduler 추가 |

---

## 17. Throttling / Sliding Window 점검

> **배경**: 현재 구현 계획에 Throttling이 어디까지 적용되어 있는지 점검하고,
> Sliding Window 방식 적용의 타당성을 쿠팡 관점에서 분석한다.

### 17.1 현재 구현 계획의 Throttling 현황

| 위치 | 적용 여부 | 방식 | 설정 |
|------|----------|------|------|
| **인바운드 API** (클라이언트 → 우리) | ❌ 미적용 | - | - |
| **아웃바운드 PG 결제 요청** (우리 → PG) | ❌ 미적용 | CB가 간접 보호 | - |
| **아웃바운드 PG 배치 조회** (우리 → PG) | ✅ 적용 | Fixed Window Rate Limiter | 10 req/sec |
| **CB Sliding Window** | ✅ 적용 | COUNT_BASED | size: 10 |

**진단**: 배치 조회에만 Rate Limiter가 있고, 인바운드/아웃바운드 결제 요청에는 Throttling이 없다.

### 17.2 Throttling이 필요한 3가지 지점

#### (1) 인바운드 API Throttling — 클라이언트 요청 제한

```
[클라이언트] ---(초당 1000건)--→ [결제 API] --→ [PG]
```

- **현재**: 제한 없음. 클라이언트가 무제한 요청 가능
- **위험**: 트래픽 급증 시 PG로의 요청도 급증 → PG CB Open → 전체 결제 불가
- **필요성**: 🟡 중간 (현재 과제 범위에서는 API Gateway/Nginx 레벨 처리가 일반적)

```
[적용 방안]
- 현재 과제: Spring Boot 내 Rate Limiter로 간단 적용 가능
- 프로덕션: API Gateway (Kong, Nginx) 레벨에서 처리
- 이유: 인바운드 Throttling은 인프라 레벨 관심사이며,
        애플리케이션에서 하면 인스턴스별로 설정이 분산됨
```

#### (2) 아웃바운드 PG 결제 요청 Throttling — PG 보호

```
[결제 API] ---(동시 100건)--→ [PG] → 과부하
```

- **현재**: CB가 실패율 기반으로 간접 보호하지만, "정상 상황에서의 과부하"는 막지 못함
- **위험**: 플래시 세일 → 동시 결제 폭증 → PG 처리 한계 초과 → 응답 지연 → CB Open
- **필요성**: 🔴 높음 (PG 계약에 TPS 제한이 있는 경우 필수)

```yaml
# 결제 요청 Rate Limiter
resilience4j:
  ratelimiter:
    instances:
      pgPaymentRequest:
        limit-for-period: 50          # 초당 최대 50건
        limit-refresh-period: 1s
        timeout-duration: 2s          # 초과 시 2초 대기 후 재시도
```

#### (3) 아웃바운드 PG 배치 조회 Throttling — 이미 적용됨

- **현재**: `pgStatusBatch` Rate Limiter (10 req/sec) ✅
- **상태**: 적절하게 적용됨

### 17.3 Fixed Window vs Sliding Window — 상세 트레이드오프

#### Fixed Window (현재 적용 방식)

```
|-------- 1초 구간 --------|-------- 1초 구간 --------|
[   10건 허용              ][   10건 허용              ]
                        ↑
                   구간 경계에서 리셋
```

- **구현**: Resilience4j `RateLimiter` 기본 방식
- **장점**: 제로 코드 (어노테이션만으로 적용), 메트릭/Actuator 자동 연동
- **문제**: **경계 돌파(Boundary Burst)**

```
예시: limit = 50 req/sec (결제 요청 Rate Limiter)

시간: 0.0s ─────── 0.95s ── 1.0s ── 1.05s ─────── 2.0s
구간:  [──── 1초 구간 A ────][──── 1초 구간 B ────]
요청:              50건 ↗       50건 ↗
                   (0.95s)       (1.0s)

→ 0.1초 동안 100건 통과! (의도한 50 req/sec의 2배)
→ PG가 계약 TPS 50인데 순간 100건을 받는 상황
```

경계 돌파가 실제로 발생하는 조건:
1. 동시 요청이 **윈도우 끝**에 몰려야 함
2. 그 직후 새 윈도우 시작 시점에도 요청이 몰려야 함
3. 즉, **트래픽이 특정 시점에 집중**될 때 발생

```
쿠팡에서 경계 돌파가 발생하는 실제 상황:
- 플래시 세일 시작 직후 (정각에 트래픽 폭증)
- 쿠폰 발급 이벤트 (특정 시각에 사용자 집중)
- 타임딜 만료 직전 (마감 효과로 결제 집중)

→ 이커머스에서는 "트래픽이 특정 시점에 집중"되는 것이 일상
→ Fixed Window의 경계 돌파는 이론적 문제가 아니라 실제 문제
```

#### Sliding Window Counter (대안)

```
Fixed Window A의 잔여 비중 × A의 카운트 + Window B의 카운트 ≤ limit

예시: limit = 50, 현재 시각 = 1.3초
  Window A (0~1초): 40건
  Window B (1~2초): 15건

  A의 잔여 비중 = 1.0 - 0.3 = 0.7
  가중 합계 = 0.7 × 40 + 15 = 28 + 15 = 43 → 50 이하 → 허용
```

- **핵심**: Fixed Window 2개의 카운터를 보간하여 **근사 Sliding Window** 구현
- **정확도**: 요청 분포가 균일하다고 가정 → 실제 오차는 ±수 건 이내
- **채택 사례**: Cloudflare, Nginx, Kong, Stripe API

### 17.4 Sliding Window Counter 구현 상세

#### 17.4.1 구현 코드 (in-memory, 단일 인스턴스용)

```java
@Component
public class SlidingWindowRateLimiter {

    private final int limit;
    private final long windowSizeMs;

    private final AtomicLong prevWindowStart = new AtomicLong(0);
    private final AtomicInteger prevWindowCount = new AtomicInteger(0);
    private final AtomicLong currWindowStart = new AtomicLong(0);
    private final AtomicInteger currWindowCount = new AtomicInteger(0);

    public SlidingWindowRateLimiter(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs * windowSizeMs;

        // 윈도우 전환 처리
        if (currentWindow != currWindowStart.get()) {
            prevWindowCount.set(currWindowCount.get());
            prevWindowStart.set(currWindowStart.get());
            currWindowCount.set(0);
            currWindowStart.set(currentWindow);
        }

        // 이전 윈도우의 잔여 비중 계산
        long elapsed = now - currentWindow;
        double prevWeight = Math.max(0, 1.0 - (double) elapsed / windowSizeMs);

        // 가중 합계
        double weightedCount = prevWeight * prevWindowCount.get() + currWindowCount.get();

        if (weightedCount < limit) {
            currWindowCount.incrementAndGet();
            return true;
        }
        return false;
    }
}
```

#### 17.4.2 구현 복잡도 분석

| 항목 | Fixed Window (Resilience4j) | Sliding Window Counter (직접 구현) |
|------|---------------------------|----------------------------------|
| **코드량** | 0줄 (어노테이션 + yaml) | ~50줄 (위 코드) |
| **메모리** | Resilience4j 내부 관리 | `AtomicLong` 2개 + `AtomicInteger` 2개 = 24바이트/인스턴스 |
| **CPU** | 카운터 비교 O(1) | 곱셈 1회 + 덧셈 1회 + 비교 O(1) |
| **스레드 안전성** | Resilience4j 보장 | `synchronized` 블록 필요 |
| **메트릭** | Actuator 자동 연동 | Micrometer 직접 등록 필요 |
| **어노테이션 통합** | `@RateLimiter` 자동 | 직접 AOP 또는 인터셉터 작성 |
| **설정 변경** | yaml 수정만으로 | 코드 수정 또는 동적 설정 직접 구현 |
| **테스트** | Resilience4j 테스트 유틸 활용 | 시간 제어 테스트 직접 작성 (Clock 주입 등) |

#### 17.4.3 "복잡도 증가"의 실체 — 코드 50줄이 문제가 아니다

```
Sliding Window Counter를 도입하면 발생하는 실제 비용:

1. Resilience4j 어노테이션 생태계에서 이탈
   - @RateLimiter 대신 커스텀 AOP 또는 인터셉터
   - @CircuitBreaker, @Retry와의 실행 순서를 직접 관리
   - Resilience4j의 이벤트 시스템(onSuccess, onFailure)과 분리

2. 메트릭/모니터링 직접 구축
   - Resilience4j는 Actuator에 자동으로 메트릭 노출
   - 커스텀 Rate Limiter는 Micrometer Counter/Gauge 직접 등록
   - Grafana 대시보드도 별도 구성

3. 설정 관리 이원화
   - CB/Retry는 application.yml에서 관리
   - Sliding Window Rate Limiter는 별도 방식으로 관리
   - 운영 중 설정 변경 시 혼란

4. 테스트 복잡도
   - 시간 의존 로직 → Clock 주입 또는 TestClock 필요
   - 멀티스레드 동시성 테스트 직접 작성
   - Fixed Window는 Resilience4j가 테스트 유틸 제공
```

### 17.5 적용 지점별 판단

#### (1) 배치 Rate Limiter (pgStatusBatch) — Fixed Window 유지 ✅

```
판단: Fixed Window 유지

이유:
- 배치 스케줄러가 1건씩 순차 호출 → 동시성 자체가 없음
- 경계 돌파 전제 조건(트래픽 집중)이 구조적으로 불가능
- Sliding Window를 적용해도 동작 차이 없음 → 불필요한 복잡성
```

#### (2) 결제 요청 Rate Limiter (pgPaymentRequest) — 판단이 갈리는 지점

```
결제 요청은 동시 트래픽이 실제로 발생하는 지점이다.
Fixed Window의 경계 돌파가 실제 문제가 될 수 있다.
```

| 관점 | Fixed Window 유지 | Sliding Window Counter 적용 |
|------|------------------|---------------------------|
| **PG 보호** | 순간 2배 burst → PG 과부하 가능 | 정확한 TPS 제한 → PG 안전 |
| **구현 비용** | 0줄 | ~50줄 + AOP + 메트릭 |
| **Resilience4j 통합** | 완벽 | 분리됨 (실행 순서 직접 관리) |
| **운영 비용** | yaml 변경만으로 제한 조절 | 코드 변경 또는 동적 설정 필요 |
| **경계 돌파 대안** | limit을 보수적으로 설정 (50 → 30) | 불필요 |
| **분산 환경 확장** | 인스턴스별 분산 (3대면 150 TPS) | 동일 문제 (Redis 필요) |

#### "limit을 보수적으로 설정"하면 해결되는가?

```
PG 계약: 50 TPS
경계 돌파 최대: 2배 = 100 TPS

대안 1: limit = 25로 설정 → 경계 돌파 시 최대 50 TPS → PG 안전
         단점: 정상 상태에서도 25 TPS로 제한 → 처리량 50% 낭비

대안 2: limit = 40로 설정 → 경계 돌파 시 최대 80 TPS → PG 약간 초과
         단점: PG가 80 TPS를 일시적으로 견딜 수 있는지에 의존

대안 3: Sliding Window Counter → 정확히 50 TPS → PG 안전 + 처리량 최대
         단점: 구현 비용 + Resilience4j 생태계 이탈
```

```
쿠팡 관점 분석:
- PG 계약 TPS가 50이면, 25로 제한하는 건 비즈니스 손실
- 플래시 세일에서 초당 결제 25건 vs 50건 → 매출 차이 큼
- 따라서 "보수적 limit"은 해결책이 아니라 회피

그러나:
- 현재 과제는 단일 인스턴스 + PG 시뮬레이터
- PG 시뮬레이터에 엄격한 TPS 제한이 없음
- Resilience4j 통합 유지의 가치가 높음 (학습 과제 특성)
```

#### (3) CB의 Sliding Window — COUNT_BASED 유지 ✅

```yaml
sliding-window-type: COUNT_BASED   # 최근 N건 기준
sliding-window-size: 10            # 최근 10건 중 실패율 계산
```

```
판단: COUNT_BASED 유지

이유:
- COUNT_BASED는 트래픽이 적을 때도 정확 (10건이 쌓이면 즉시 판단)
- TIME_BASED는 트래픽이 적으면 10초 구간에 2건만 들어올 수 있음
  → 2건 중 1건 실패 = 50% 실패율 → CB Open (과민 반응)
- 현재 과제의 PG 트래픽은 고정적이지 않으므로 COUNT_BASED가 안전

참고: CB의 sliding window와 Rate Limiter의 sliding window는 다른 개념
- CB: 최근 N건/N초의 "실패율"을 측정 (판단 기준)
- Rate Limiter: 시간당 "요청 수"를 제한 (흐름 제어)
```

### 17.6 최종 판단

```
[결론: 결제 요청에 Sliding Window Counter 적용]

1. 배치 Rate Limiter (pgStatusBatch):
   - Fixed Window 유지 (Resilience4j @RateLimiter)
   - 이유: 순차 처리 → 경계 돌파 불가능

2. 결제 요청 Rate Limiter (pgPaymentRequest):
   - Sliding Window Counter 적용 (직접 구현)
   - 이유:
     a) 결제는 동시 트래픽이 발생하는 유일한 아웃바운드 지점
     b) PG TPS 제한은 계약 사항 — 초과하면 차단당할 수 있음
     c) "limit을 보수적으로 설정"하면 정상 시 처리량이 낭비됨
     d) Resilience 과제에서 Rate Limiting 전략을 직접 구현해보는 학습 가치

   구현 범위:
   - SlidingWindowRateLimiter 클래스 (~50줄)
   - PaymentRateLimiterInterceptor (AOP)
   - Micrometer 메트릭 등록 (허용/거부 카운터)

   Resilience4j 통합 대안:
   - @RateLimiter 대신 Interceptor로 적용
   - 실행 순서: SlidingWindowRateLimiter → @Retry → @CircuitBreaker

3. CB Sliding Window:
   - COUNT_BASED 유지

4. Redis 기반 Sliding Window (분산 환경):
   - 현재 과제: 단일 인스턴스 → in-memory 충분
   - 프로덕션: Redis Sorted Set 기반으로 교체
     (§16에서 Redis를 이미 사용하므로 인프라 추가 비용 없음)
```

### 17.7 결제 요청 Rate Limiter 설계

#### Sliding Window Counter 설정

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public SlidingWindowRateLimiter pgPaymentRateLimiter() {
        return new SlidingWindowRateLimiter(
            50,         // limit: 초당 최대 50건
            1000        // windowSizeMs: 1초
        );
    }
}
```

#### Rate Limiter + Retry + CB 실행 순서

```
[클라이언트 요청]
   │
   ▼
[SlidingWindowRateLimiter]  ← 최근 1초간 50건 초과? → 429 또는 큐잉
   │ (통과)
   ▼
[@Retry]                    ← 실패 시 1회 재시도 (500ms 대기)
   │
   ▼
[@CircuitBreaker]           ← 실패율 50% 초과? → Fallback (다른 PG)
   │
   ▼
[Feign Client]              → PG 호출

[배치 조회]
   │
   ▼
[@RateLimiter(pgStatusBatch)]  ← Fixed Window 10 req/sec (Resilience4j)
   │
   ▼
[@CircuitBreaker]              → PG 조회
```

```
실행 순서 근거:
- Sliding Window Rate Limiter가 가장 바깥:
  PG로 나가는 총량을 먼저 제한 (계약 TPS 보호)
- Retry가 CB 바깥:
  재시도 실패도 CB에 기록되어야 정확한 실패율 측정
- CB가 가장 안쪽:
  최종 차단 판단 + Fallback 트리거
- Rate Limiter 거부는 CB에 기록하지 않음:
  Rate Limiter 거부 = PG 장애가 아닌 트래픽 초과
  CB에 기록하면 트래픽만 많아도 CB Open → 오작동
```

#### 배치 Rate Limiter — Fixed Window 유지

```yaml
resilience4j:
  ratelimiter:
    instances:
      pgStatusBatch:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0
```

#### 현재 과제 적용 범위

| 항목 | 방식 | 이유 |
|------|------|------|
| 결제 요청 Rate Limiter | Sliding Window Counter (직접 구현) | PG TPS 보호 + 경계 돌파 방지 + 학습 가치 |
| 배치 Rate Limiter | Fixed Window (Resilience4j) | 순차 처리, 경계 돌파 불가 |
| CB Sliding Window | COUNT_BASED (Resilience4j) | 트래픽 변동에 안정적 |
| 인바운드 API Throttling | ❌ 미적용 | API Gateway/인프라 관심사 |
| Redis 분산 Rate Limiting | ❌ 미적용 (단일 인스턴스) | 프로덕션에서 Redis Sorted Set으로 확장 |

### 17.8 → 05 반영 사항

| 반영 대상 | 내용 |
|----------|------|
| Section 7 | 결제 요청: Sliding Window Counter, 배치: Fixed Window (Resilience4j) |
| Section 7.5 | SlidingWindowRateLimiter → @Retry → @CircuitBreaker 실행 순서 |
| Section 15 | Phase 2에 SlidingWindowRateLimiter 구현 + Interceptor 항목 추가 |
| Section 16 | SlidingWindowRateLimiter 클래스, PaymentRateLimiterInterceptor 패키지 추가 |

---

## 18. CB 읽기/쓰기 분석 — 읽기 CB 제거 근거

> **질문**: CB를 읽기/쓰기 구분 없이 모든 외부 호출에 적용했는데,
> 쓰기에만 CB를 걸고 읽기에는 걸지 않아도 되지 않은가?

### 18.1 현재 CB 인스턴스 (8개)

| CB 인스턴스 | 성격 | 대상 |
|---|---|---|
| `pgSimulator-request` | **쓰기** | PG 결제 요청 (POST) |
| `pgSimulator-status-realtime` | 읽기 | PG 상태 확인 - 실시간 |
| `pgSimulator-status-batch` | 읽기 | PG 상태 확인 - 배치 |
| `pgToss-request` | **쓰기** | Toss 결제 요청 (POST) |
| `pgToss-status-realtime` | 읽기 | Toss 상태 확인 - 실시간 |
| `pgToss-status-batch` | 읽기 | Toss 상태 확인 - 배치 |
| `redis-write` | **쓰기** | Redis 가주문 쓰기 |
| `redis-read` | 읽기 | Redis 가주문 조회 |

쓰기 3개, 읽기 5개.

### 18.2 읽기 CB가 불필요한 3가지 근거

#### (1) 상태 확인은 "복구 행위" — CB가 차단하면 복구가 멈춘다

```
PG 결제 요청 → 타임아웃 → 내부 상태 UNKNOWN
→ PG 상태 확인 API로 "결제 됐어?" 확인 필요 ← 이것이 복구 경로

그런데 status CB Open이면?
→ 상태 확인 자체가 차단 → 복구 불가
→ PENDING/UNKNOWN 건이 계속 쌓임

더 나쁜 케이스:
  PG가 3초 만에 복구됨
  status CB는 Open (wait 5초)
  → 2초간 불필요한 복구 지연
  → 쿠팡 기준: 초당 1000건 × 2초 = 2000건 결제 확인 지연
```

**CB의 목적은 장애 전파 방지**인데,
상태 확인을 차단하는 건 **전파 방지가 아니라 복구 방해**다.

#### (2) 읽기에는 이미 다른 보호 수단이 있다

| 읽기 대상 | 기존 보호 | CB와 중복? |
|---|---|---|
| PG status (실시간) | Feign Timeout 1초 | **중복** — timeout으로 빠른 실패 보장 |
| PG status (배치) | Rate Limiter 10 req/sec + Timeout | **중복** — 배치 스레드 1개, 부하 미미 |
| Redis read | Lettuce `commandTimeout 500ms` + `ReadFrom.REPLICA_PREFERRED` | **중복** — 드라이버 내장 폴백 |

```
[PG status-batch, CB 없이 PG 전면 장애 시]
  Rate Limiter: 10 req/sec
  배치 스레드: 1개 (순차 처리)
  Timeout: 1초/건
  → 초당 1 스레드 × 1초 = 1 스레드 점유
  → 위험 없음

[redis-read, CB 없이 전체 Redis 장애 시]
  Replica 장애 → Lettuce가 Master로 자동 폴백 → CB 불필요
  전체 장애 → commandTimeout 500ms → try-catch → DB Fallback
  redis-write CB Open → 새 가주문은 DB → Redis 읽기 시도 자체 감소
  → Timeout + try-catch으로 충분
```

#### (3) 읽기 CB가 만드는 부작용

```
redis-read CB Open 중 Replica 복구됨
→ wait-duration 3초간 Redis 읽기 차단
→ 모든 가주문 조회가 DB로 감 (불필요한 DB 부하)
→ Lettuce의 REPLICA_PREFERRED 자동 폴백도 무력화

status-realtime CB Open 중 PG 복구됨
→ wait-duration 5초간 상태 확인 차단
→ UNKNOWN 건 복구가 5초 지연
→ 고객은 "결제 처리 중" 화면을 5초 더 봄
```

CB가 읽기를 차단하면, **드라이버/인프라 레벨의 내장 폴백보다 느리게 동작**한다.

### 18.3 결론: 쓰기 CB만 유지 (8개 → 3개)

#### 유지 (쓰기 3개)

| CB | 근거 |
|---|---|
| `pgSimulator-request` | 결제 요청 차단 + Fallback PG 전환. **돈이 걸린 쓰기** |
| `pgToss-request` | 위와 동일 |
| `redis-write` | 가주문 쓰기 차단 + DB Fallback. **재고 차감이 걸린 쓰기** |

#### 제거 (읽기 5개)

| CB | 제거 근거 | 대체 보호 |
|---|---|---|
| `pgSimulator-status-realtime` | 복구 경로 차단 방지 | Timeout 1초 |
| `pgSimulator-status-batch` | Rate Limiter와 중복 | Rate Limiter + Timeout |
| `pgToss-status-realtime` | 위와 동일 | Timeout 1초 |
| `pgToss-status-batch` | 위와 동일 | Rate Limiter + Timeout |
| `redis-read` | Lettuce 내장 폴백 무력화 방지 | `commandTimeout 500ms` + try-catch |

```
CB 관리 복잡성: 62.5% 감소 (8개 → 3개)
모니터링 대상: 62.5% 감소
Half-Open 전략: 쓰기 CB에만 집중 가능
복구 경로: CB에 의한 차단 없이 즉시 동작
```

### 18.4 읽기 보호 — CB 제거 후 코드 패턴

```java
// 상태 조회 — CB 없음, Timeout만으로 보호
// @CircuitBreaker 제거, @RateLimiter(배치)만 유지
public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
    try {
        return pgClient.getPaymentStatus(transactionKey);  // Feign timeout 1초
    } catch (Exception e) {
        log.warn("PG 상태 확인 실패: transactionKey={}", transactionKey, e);
        return PgPaymentStatusResponse.unknown(transactionKey);
    }
}

// Redis 읽기 — CB 없음, try-catch + Timeout으로 보호
public Optional<ProvisionalOrder> findProvisionalOrder(String orderId) {
    try {
        Map<Object, Object> data = defaultRedisTemplate.opsForHash()
            .entries("provisional:order:" + orderId);  // commandTimeout 500ms
        return data.isEmpty()
            ? Optional.empty()
            : Optional.of(ProvisionalOrder.from(data));
    } catch (Exception e) {
        log.warn("Redis 가주문 조회 실패 — DB Fallback: orderId={}", orderId, e);
        return orderRepository.findByOrderId(orderId)
            .map(ProvisionalOrder::fromDbOrder);
    }
}
```

### 18.5 원칙 정리

```
CB 적용 기준: "이 호출이 실패하면 부작용(side-effect)이 있는가?"

쓰기 (CB 필요):
  - 결제 요청 → 돈이 빠질 수 있음 → CB로 차단 + Fallback PG
  - Redis 쓰기 → 재고 차감 + 가주문 생성 → CB로 차단 + DB Fallback

읽기 (CB 불필요):
  - 상태 확인 → 부작용 없음 + 복구 행위 → Timeout만으로 충분
  - Redis 읽기 → 부작용 없음 → 드라이버 폴백 + Timeout
```

### 18.6 → 05 반영 사항

| 반영 대상 | 내용 |
|---|---|
| Section 7 (CB 설정) | CB 인스턴스 8개 → 3개, 읽기 CB 설정 제거 |
| Section 7.5 (실행 순서) | status 메서드에서 @CircuitBreaker 제거 |
| Section 7.6 (Half-Open) | status CB 관련 Half-Open 전략 제거 |
| 장애 격리 검증 | redis-read 관련 행 수정 |
| Phase 3 | Redis CB 2개 → 1개 |

---

## 19. 선차감/후차감 분석 — 결제 전 자원 차감 원칙

> **질문**: 재고/포인트가 결제에 의해 차감되어야 하는데,
> 선차감/후차감 중 무엇이 계획인가?
> 사용자가 결제 롤백을 경험하지 않으려면 선차감이 좋지 않은가?

### 19.1 현재 설계 상태

| 자원 | 차감 시점 | 설계 여부 |
|---|---|---|
| **재고** | 선차감 (가주문 시 Redis DECR) | ✅ 설계됨 (§16.3 Option C) |
| **쿠폰** | — | ❌ **가주문 flow에서 빠져있음** |
| **포인트** | — | 현재 프로젝트에 없음 |

기존 코드(`OrderFacade.createOrder()`)에서는 재고와 쿠폰 모두 주문 생성 트랜잭션 안에서 처리:
```java
// 재고 차감 (line 83-86)
product.decreaseStock(req.quantity());

// 쿠폰 적용 (line 97-100)
CouponApplyResult result = couponFacade.applyCouponToOrder(
    couponIssueId, memberId, originalTotalPrice);
```

그러나 가주문 flow(§16.3 Option C)에서는 재고만 Redis DECR로 선차감하고,
**쿠폰은 설계에 포함되지 않았다.**

### 19.2 후차감의 치명적 문제 — "결제 롤백" UX

```
[후차감 시나리오]
1. 결제 요청 → PG 승인 ✅ (돈 빠짐)
2. 재고 차감 시도 → 재고 부족 ❌
3. PG 취소 API 호출 필요
4. PG 취소 실패? → 돈은 빠졌는데 상품도 없음

고객 경험: "결제 완료되었습니다" → "주문이 취소되었습니다. 환불 처리 중입니다."
→ 최악의 UX + CS 폭주
```

```
[선차감 시나리오]
1. 재고/쿠폰 선차감 ✅
2. 결제 요청 → PG 실패 ❌
3. 재고/쿠폰 복원
4. 복원 실패? → 자원 일시 잠김 → 배치 복원 → 돈 관련 문제 0

고객 경험: "결제에 실패했습니다. 다시 시도해주세요."
→ 자연스러운 흐름 + PG 취소 API 자체가 불필요
```

| 구분 | 선차감 | 후차감 |
|---|---|---|
| 최악의 경우 | 자원 일시 잠김 (배치 복원) | **돈 빠짐 + 상품 없음** |
| PG 취소 API 필요? | 불필요 | **필수** (추가 외부 의존성) |
| 복원 실패 영향 | 자원 잠김 (비즈니스) | **환불 지연** (금전) |
| 장애 포인트 수 | 복원만 (INCR, DB UPDATE) | **PG 취소 + 환불 확인 + 재고 복원** |

**원칙: 차감 가능한 모든 자원은 결제 전 선차감.**

### 19.3 쿠폰 선차감 — 가주문 flow 보완

#### 재고와 쿠폰의 동시성 차이

| 비교 | 재고 | 쿠폰 |
|---|---|---|
| 동시성 | 높음 (같은 상품 수백 명) | **낮음** (1인 1쿠폰, 본인만 사용) |
| 호출 빈도 | 모든 주문 | 쿠폰 있는 주문만 (optional) |
| Redis 필요? | ✅ 플래시 세일 대응 | ❌ DB UPDATE 1건으로 충분 |

#### 쿠폰 선차감 설계

```
[보완된 가주문 flow]
가주문 생성:
  1. Redis DECR(stock)                          ← 재고 선차감 (Redis)
  2. DB: 쿠폰 상태 USED 처리 (쿠폰 있는 경우)    ← 쿠폰 선차감 (DB)
  3. Redis HSET(가주문, couponIssueId 포함)
  → 결제 요청

결제 성공 (진주문 전환):
  → DB INSERT(주문) + DB 재고 확정

결제 실패:
  → Redis INCR(stock)                ← 재고 복원
  → DB: 쿠폰 상태 AVAILABLE 복원     ← 쿠폰 복원
```

#### "가주문에서 DB 트랜잭션을 열면 가주문의 목적이 반감되지 않나?"

```
가주문의 목적: DB 쓰기 부하 감소 (결제 전 주문을 DB에 INSERT하지 않음)

쿠폰 선차감: DB UPDATE 1건 (CouponIssue.status = USED)
주문 INSERT: 없음 (Redis에만 저장)

비교:
  기존 (가주문 없이):  DB INSERT(Order) + DB INSERT(OrderItems) + DB UPDATE(Stock) + DB UPDATE(Coupon)
  가주문 + 쿠폰 선차감: DB UPDATE(Coupon) 1건만

→ DB 부하 감소 효과 대부분 유지 (4건 → 1건)
→ 쿠폰 없는 주문은 DB 트랜잭션 0건 (완전한 가주문)
```

#### 쿠폰 복원 실패 시 대응

```
결제 실패 → 쿠폰 복원 시도 → 복원 실패 (DB 일시 장애)

대응:
  1. 즉시: 로그 + 모니터링 알림
  2. 배치: 결제 FAILED + 쿠폰 USED → 쿠폰 AVAILABLE 복원 (30초 주기)
  3. 영향: 쿠폰 일시 사용 불가 → 재결제 시 쿠폰 선택 불가 → 쿠폰 없이 결제 가능

  최악: 쿠폰 잠김 (비즈니스 손실 미미)
  후차감 최악: 돈 빠짐 + 환불 대기 (금전 손실)
  → 비교 불가
```

### 19.4 → 05 반영 사항

| 반영 대상 | 내용 |
|---|---|
| 가주문 flow | 쿠폰 선차감 단계 추가 |
| Phase 1 | 가주문 모델에 couponIssueId 필드 추가 |
| Phase 4 | 결제 실패 시 쿠폰 복원 로직 추가 |
| 결제 실패 처리 (Section 13.2) | 쿠폰 복원 행 추가 |

---

## 20. 쿠폰 선차감 — Redis 불필요 근거 + 트랜잭션 경계 보완

> **질문 1**: 다양한 종류의 쿠폰이 발행되는 상황에서도 Redis 없이 DB만으로 유지 가능한가?
> **질문 2**: 외부 API 호출과 내부 처리가 하나의 트랜잭션으로 묶여있지 않은가?

### 20.1 쿠폰 "발급"과 "사용"의 구분

쿠폰 라이프사이클에서 동시성이 높은 지점과 낮은 지점이 다르다:

```
[발급] 쿠폰 템플릿 → 사용자에게 CouponIssue 생성 (INSERT)
[사용] CouponIssue의 status를 AVAILABLE → USED (UPDATE)
[복원] CouponIssue의 status를 USED → AVAILABLE (UPDATE)
```

| 단계 | 경합 대상 | 동시성 | Redis 필요? |
|---|---|---|---|
| **발급** | 쿠폰 템플릿의 수량 한도 | 높음 (선착순 1000명) | 상황에 따라 ⚠️ |
| **사용** (결제 시 선차감) | 개인의 CouponIssue 1건 | **낮음** (본인 1명) | ❌ 불필요 |
| **복원** (결제 실패 시) | 개인의 CouponIssue 1건 | **낮음** (본인 1명) | ❌ 불필요 |

### 20.2 결제 시 "사용" — DB만으로 충분한 이유

#### Coupon(템플릿)과 CouponIssue(발급 건)의 관계

```
[Coupon] 1 ────── N [CouponIssue]

Coupon (쿠폰 템플릿):
  id: 1, name: "여름 세일 10% 할인"    ← 쿠폰 정의 (1건)

CouponIssue (발급 건):
  id: 101, coupon_id: 1, member_id: 유저A, status: AVAILABLE  ← 유저A의 쿠폰
  id: 102, coupon_id: 1, member_id: 유저B, status: AVAILABLE  ← 유저B의 쿠폰
  id: 103, coupon_id: 1, member_id: 유저C, status: USED       ← 유저C의 쿠폰 (사용됨)
```

같은 쿠폰 템플릿을 여러 사용자가 발급받을 수 있다.
발급 시마다 개인별 CouponIssue row가 생성된다.

#### 사용 시 경합이 없는 구조적 이유

```java
// markAsUsed() — CouponIssue.id (발급 건의 PK)로 UPDATE
// coupon_id(템플릿)가 아님!
@Query("UPDATE CouponIssue ci SET ci.status = :usedStatus"
    + " WHERE ci.id = :id AND ci.status = :availableStatus AND ci.expiredAt > :now")
int markAsUsed(@Param("id") Long id, ...);
```

```
1000명이 같은 쿠폰 템플릿(coupon_id=1)으로 동시에 결제해도:

유저A: UPDATE coupon_issue SET status='USED' WHERE id=101  ← row 101
유저B: UPDATE coupon_issue SET status='USED' WHERE id=102  ← row 102
유저C: UPDATE coupon_issue SET status='USED' WHERE id=103  ← row 103

→ 각자 다른 row를 UPDATE → DB 경합 없음
→ 재고와 구조적으로 다름:
  재고: UPDATE product SET stock=stock-1 WHERE id=상품A ← 같은 row에 1000명 경합
  쿠폰: UPDATE coupon_issue SET status='USED' WHERE id=내_발급건 ← 각자 다른 row

이것이 쿠폰 사용에 Redis가 불필요한 근본 이유다.
```

#### CAS가 방어하는 유일한 경합 시나리오

```
같은 사람이 동시에 2개 주문에서 같은 쿠폰을 사용하는 경우:

요청 1: UPDATE coupon_issue SET status='USED' WHERE id=101 AND status='AVAILABLE'
  → updated = 1 ✅
요청 2: UPDATE coupon_issue SET status='USED' WHERE id=101 AND status='AVAILABLE'
  → updated = 0 ❌ (이미 USED → WHERE 조건 불일치)

→ 중복 사용 방지 완료
```

### 20.3 쿠폰 유형별 분석

| 쿠폰 유형 | 발급 시 동시성 | 사용(결제) 시 동시성 | DB로 충분? |
|---|---|---|---|
| 개인 발급 쿠폰 | 본인 요청 | 각자의 CouponIssue row | ✅ |
| 신규가입/생일 쿠폰 | 이벤트 트리거 | 각자의 CouponIssue row | ✅ |
| 선착순 한정 쿠폰 | **높음** (1000명 동시 발급) | 발급 이후 각자의 row → 경합 없음 | ✅ |
| 금액/비율 할인 | 발급 방식에 따라 다름 | 각자의 CouponIssue row | ✅ |
| 코드 입력 쿠폰 | 코드당 수량 제한 시 높음 | 발급 이후 각자의 row → 경합 없음 | ✅ |

```
핵심 인사이트:

쿠폰 "발급"과 "사용"의 동시성은 독립적이다.

발급: Coupon(템플릿)의 수량을 차감 → 같은 row에 N명 경합 → 동시성 높음
사용: CouponIssue(발급 건)의 status를 변경 → 각자 다른 row → 경합 없음

발급 시점에 동시성이 아무리 높아도,
발급 이후에는 개인별 CouponIssue row로 분리되므로 사용 시 경합이 없다.

→ 발급 시 Redis가 필요할 수 있지만, 사용(결제 선차감) 시에는 DB CAS로 충분하다.

결론: 쿠폰 종류가 다양해져도, 같은 쿠폰을 여러 명이 발급받아도,
      결제 시 선차감은 DB CAS 유지.
```
```

### 20.4 트랜잭션 경계 — 외부 API 분리 확인

#### 현재 설계 (05 §12): PG 호출은 트랜잭션 밖 ✅

```
[TX-1] Payment(REQUESTED) + Outbox(PENDING) → commit
[PG 호출] CB → Retry → PG 요청 (트랜잭션 없음)
[TX-2] Payment 상태 업데이트 → commit
```

#### 가주문 flow에 쿠폰 선차감 추가 시 트랜잭션 경계

기존 §12 설계에는 가주문 + 쿠폰 선차감의 트랜잭션이 명시되지 않았다.
보완한 전체 흐름:

```
[TX-0] 쿠폰 USED 처리 (CAS UPDATE 1건) → commit     ← 쿠폰 선차감
[Redis] DECR(stock) + HSET(가주문, couponIssueId)     ← 재고 선차감 + 가주문 생성
[TX-1] Payment(REQUESTED) + Outbox(PENDING) → commit  ← 결제 기록
[PG 호출] CB → Retry → PG 요청                         ← 트랜잭션 없음
[TX-2] Payment 상태 업데이트 → commit                   ← PG 응답 처리

... (콜백 수신 시) ...

[TX-3] Payment 확정 + Order 상태 + Redis DEL → commit  ← 진주문 전환
```

#### 각 TX의 커넥션 점유 시간

```
TX-0: CAS UPDATE 1건 → ~5ms    (쿠폰 없는 주문은 TX-0 자체가 없음)
TX-1: INSERT 2건 → ~10ms
PG 호출: 100ms~4.5초            ← 트랜잭션 없음, DB 커넥션 0개 점유
TX-2: UPDATE 1건 → ~5ms
TX-3: UPDATE 2건 + Redis DEL → ~10ms

총 DB 커넥션 점유: ~30ms (PG 지연과 무관)
비교: PG 호출이 TX 안이면 → 최대 4.5초 점유 (150배 차이)
```

#### 산술적 검증

```
초당 100건 결제 시:

[트랜잭션 분리 (현재)]
  100건 × 30ms = 3 커넥션·초
  HikariCP 10개 → 사용률 30% → 안전

[PG 호출이 TX 안 (만약)]
  100건 × 4.5초 = 450 커넥션·초
  HikariCP 10개 → 사용률 4500% → 즉시 고갈
  → 결제 장애가 상품 조회, 주문 조회까지 전파
```

### 20.5 TX-0 실패 시나리오별 보상

| 시나리오 | 상태 | 보상 |
|---|---|---|
| TX-0 성공 → Redis DECR 실패 (CB Open) | 쿠폰 USED, 재고 미차감 | DB Fallback 경로 진입 (DB 재고 차감) |
| TX-0 성공 → 결제 성공 | 쿠폰 USED, 진주문 확정 | 보상 불필요 ✅ |
| TX-0 성공 → 결제 실패 | 쿠폰 USED, 결제 FAILED | 쿠폰 복원: `couponFacade.restoreCoupon()` |
| TX-0 성공 → 결제 실패 → 쿠폰 복원 실패 | 쿠폰 잠김 | 배치: 결제 FAILED + 쿠폰 USED → 복원 |
| TX-0 실패 (CAS 실패) | 쿠폰 이미 사용/만료 | 즉시 에러 응답 ("사용할 수 없는 쿠폰") |

```
TX-0 성공 → Redis 성공 → TX-1 성공 → PG 호출 전 서버 크래시:
  쿠폰: USED (잠김)
  재고: Redis DECR됨 (잠김)
  Payment: REQUESTED (Outbox에 기록됨)
  → Outbox Poller가 5초 후 PG 호출 재시도
  → 결제 진행 → 성공이면 모두 확정, 실패이면 모두 복원
  → 어느 경우든 정합성 유지
```

### 20.6 → 05 반영 사항

| 반영 대상 | 내용 |
|---|---|
| Section 12.2 (트랜잭션 분리) | TX-0(쿠폰 선차감) 추가, 가주문 flow 전체 TX 명시 |
| Section 12 | 커넥션 점유 시간 산술 검증 추가 |

---

## 21. 아키텍처 맥락 정리 — 모듈러 모놀리스 + MSA 전환 고려

> 설계 문서 전반에 "모노리스" / "MSA 전환 시" 언급이 산재해 있다.
> 현재 구조의 전제와 MSA 전환 시 변경 지점을 한 곳에 정리한다.

### 21.1 현재 구조: 모듈러 모놀리스

```
[commerce-api — 단일 SpringBoot 애플리케이션]

/domain/member/       ┐
/domain/brand/        │
/domain/product/      │
/domain/order/        ├── 같은 프로세스, 같은 DB
/domain/coupon/       │
/domain/payment/      │   ← 6주차 추가
/domain/like/         ┘

특성:
  - 모든 도메인이 같은 JVM
  - 같은 MySQL 인스턴스
  - 도메인 간 호출 = in-process 메서드 호출
  - 도메인 간 트랜잭션 = 같은 DB TX로 원자적
```

**"모놀리스"이지만:**
- 도메인별 패키지 분리 (계층 + 도메인)
- DIP 적용 (Domain ← Infrastructure)
- Aggregate 간 참조는 ID로만 (느슨한 결합)
- 멀티 모듈 (apps/modules/supports)

→ MSA 경계가 패키지 레벨에서 이미 그어져 있는 **모듈러 모놀리스**.

### 21.2 모놀리스의 이점을 활용하는 현재 설계

| 설계 결정 | 모놀리스이기 때문에 가능한 것 | MSA였다면 |
|---|---|---|
| 결제 실패 → 재고 복원 | 같은 TX에서 원자적 UPDATE | 보상 이벤트 큐 or Saga |
| Payment + Order 상태 전이 | 같은 TX에서 원자적 (TX-3) | 이벤트 기반 + 최종 일관성 |
| 쿠폰 복원 | 같은 DB에서 `SELECT + UPDATE` | 쿠폰 서비스 API 호출 (실패 가능) |
| 대사 배치 | 같은 DB에서 JOIN 쿼리 | 서비스 간 API 호출 + 데이터 수집 |
| FB-COMP (보상 트랜잭션 큐) | 불필요 | Saga Pattern 필수 |

### 21.3 TX 분리의 이유 — 도메인 분리가 아니라 외부 호출 격리

```
TX-0/TX-1/TX-2 분리는 MSA 준비가 아니다.

[이유: PG 호출(외부 API)을 트랜잭션 밖으로 빼기 위함]

TX-0: 쿠폰 USED   ← 내부 (DB)
Redis: 재고 DECR   ← 내부 (Redis)
TX-1: Payment 저장 ← 내부 (DB)
PG 호출            ← 외부 (PG API) ← 이것 때문에 TX 분리
TX-2: 상태 업데이트 ← 내부 (DB)

만약 PG 호출이 없었다면:
  TX-0 + TX-1 + TX-2 = 하나의 TX로 충분 (모놀리스)

분리 기준: "외부 시스템 호출이 TX 안에 있으면 커넥션 점유 → 고갈"
분리 기준이 아닌 것: "도메인 경계" (MSA 전환 시에는 이것도 기준이 됨)
```

### 21.4 MSA 전환 시 변경 지점

| 현재 (모놀리스) | MSA 전환 시 | 변경 수준 |
|---|---|---|
| OrderFacade → CouponFacade (메서드 호출) | Order Service → Coupon Service (API/이벤트) | 인터페이스 변경 |
| 같은 TX에서 재고 + 주문 + 쿠폰 원자적 처리 | Saga Pattern (Orchestration or Choreography) | 아키텍처 변경 |
| Outbox → DB 폴링 | Outbox → Kafka 발행 (Transactional Outbox + CDC) | 인프라 변경 |
| 배치 복구: 같은 DB에서 JOIN 조회 | 서비스별 배치 + 이벤트 기반 연동 | 분산 처리 |
| FB-COMP 불필요 | Saga 보상 트랜잭션 필수 | 신규 구현 |
| TX-3 후속 처리 순차 (단일 TX, ~10ms) | 병렬 + 이벤트 기반 (서비스별 독립 TX) | 아키텍처 변경 |

> **TX-3 병렬화 판단 근거**: 모놀리스에서 같은 DB UPDATE 3~4건은 ~10ms.
> 병렬화 이득 ~5ms vs Saga 보상 로직 복잡도 → 트레이드오프 불균형.
> MSA 전환 시 서비스 분리로 단일 TX 불가 → 그때 이벤트 기반 병렬 처리가 자연스러운 전환점.

```
현재 설계가 MSA-ready인 부분:
  ✅ 도메인별 패키지 분리 → 서비스 경계 명확
  ✅ Aggregate 간 ID 참조 → 서비스 간 느슨한 결합
  ✅ Outbox 패턴 → Kafka 발행으로 자연스럽게 진화
  ✅ 조건부 UPDATE → 분산 환경에서도 동시성 보호

현재 설계가 모놀리스 전제인 부분:
  ⚠️ TX-3에서 Payment + Order + 쿠폰/재고를 원자적 처리
  ⚠️ 대사 배치에서 같은 DB JOIN 사용
  ⚠️ 쿠폰 복원 실패 시 같은 DB 배치로 보정
```

### 21.5 → 05 반영 사항

| 반영 대상 | 내용 |
|---|---|
| Section 12 | "TX 분리 이유 = 외부 호출 격리 (도메인 분리 아님)" 주석 추가 |

---

## 22. 대사 배치 프로세스 — 교차 시스템 정합성 검증

> **복구 배치**는 "우리 시스템 내부의 비정상 상태를 고치는 것"이고,
> **대사 배치**는 "두 시스템의 기록을 대조하여 불일치를 감지하는 것"이다.

### 22.1 복구 vs 대사 구분

| 구분 | 복구 (Recovery) | 대사 (Reconciliation) |
|---|---|---|
| 방향 | 내부 → 외부 확인 | 양쪽 기록 대조 |
| 대상 | 비정상 상태 (UNKNOWN, PENDING) | **정상 상태 포함 전수 검증** |
| 목적 | 즉시 상태 확정 | 불일치 감지 + 알림 |
| 주기 | 짧음 (초~분) | 길어도 됨 (시간~일) |
| 실패 시 영향 | 고객 대기 | 정산 오류 (금전) |

```
현재 배치들은 전부 "복구":
  Outbox (5초), Payment Recovery (1분), Stock Reconcile (30초)
  → "비정상 건을 찾아서 PG에 물어보고 고친다"

대사는 다른 질문:
  → "우리가 PAID로 확정한 건이, PG에서도 정말 SUCCESS인가?"
  → "PG에 SUCCESS인 건이, 우리에게도 전부 PAID로 반영되어 있는가?"
```

### 22.2 대사가 필요한 3가지 교차 지점

#### [R1] PG ↔ Payment 대사

```
시나리오 A: 우리 PAID, PG FAILED
  TX-3에서 콜백 데이터를 잘못 해석하여 PAID 처리
  → 고객 돈은 빠지지 않았는데 주문 완료 → 무료 구매

시나리오 B: PG SUCCESS, 우리 FAILED
  PENDING 5분 초과 → FAILED 처리 → 직후 PG에서 SUCCESS 처리
  → 고객 돈은 빠졌는데 주문 취소 → 환불 누락

시나리오 C: PG SUCCESS, 우리에 Payment 기록 자체 없음
  TX-1 전에 크래시 + Outbox도 없음 (WAL 실패)
  → PG에서 결제가 진행됨 → 우리 시스템에 흔적 없음
```

#### [R2] Payment ↔ Order 대사

```
시나리오: Payment PAID, Order PAYMENT_PENDING
  TX-3에서 Payment UPDATE 성공 + Order UPDATE 실패 (부분 커밋 불가능하지만,
  TX-3 이후 Order 상태 변경 로직이 별도 실행이면 발생 가능)

모놀리스에서는 같은 TX → 발생 확률 매우 낮음
MSA에서는 서비스 분리 시 발생 가능 → Saga 필요
→ 현재는 모놀리스이므로 낮은 우선순위지만, 검증 차원에서 대사
```

#### [R3] Payment ↔ Coupon 대사

```
시나리오: Payment FAILED + CouponIssue USED (복원 누락)
  TX-0(쿠폰 USED) → 결제 실패 → 쿠폰 복원 시도 → DB 일시 장애 → 복원 실패

현재 대응: 배치에서 복원 (§19.3)
대사 역할: 배치가 놓친 건이 있는지 전수 확인
```

### 22.3 대사 배치 설계

#### [R1] PG ↔ Payment 대사 배치

```
주기: 1시간 (또는 1일 1회)
대상: 최근 24시간 내 Payment 중 status = PAID 또는 FAILED

동작:
  1. Payment 테이블에서 대상 조회
     SELECT * FROM payment
     WHERE status IN ('PAID', 'FAILED')
       AND updated_at > NOW() - INTERVAL 24 HOUR
       AND reconciled = false

  2. 각 건에 대해 PG 상태 확인
     GET /api/v1/payments/{transactionKey}

  3. 대조
     | 우리 상태 | PG 상태 | 판정 |
     |----------|---------|------|
     | PAID | SUCCESS | ✅ 일치 → reconciled = true |
     | PAID | FAILED | 🔴 불일치 → 알림 + 수동 확인 대상 |
     | PAID | PENDING | 🟡 PG 미확정 → 다음 주기에 재확인 |
     | PAID | 404 | 🔴 PG에 기록 없음 → 알림 |
     | FAILED | SUCCESS | 🔴 환불 누락 → 알림 + 자동/수동 보상 |
     | FAILED | FAILED | ✅ 일치 → reconciled = true |

  4. 불일치 건 → reconciliation_mismatch 테이블에 기록 + 운영 알림
```

```
불일치 시 자동 보상 vs 수동 확인:

  PAID-FAILED 불일치:
    → 자동 보상 위험 (고객 주문 취소 → CS 발생)
    → 수동 확인 후 처리

  FAILED-SUCCESS 불일치:
    → 자동 보상 가능 (PAID로 전환 + 주문 확정)
    → 단, 이미 재주문했을 수 있으므로 조건 확인 필요

결론: 대사 배치는 "감지 + 알림"이 주 역할.
      자동 보상은 안전한 경우에만 (FAILED→SUCCESS 전환).
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

→ 결과가 0건이면 정상
→ 1건이라도 나오면 운영 알림

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

→ 결과가 있으면: 쿠폰 복원 누락
→ 자동 복원: couponFacade.restoreCoupon(couponIssueId)
→ 복원 후 로그 + 메트릭 기록
```

### 22.4 대사용 테이블

```sql
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

### 22.5 복구 배치 vs 대사 배치 전체 구조

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

```
복구와 대사의 관계:

복구가 완벽하면 대사에서 불일치가 0건이어야 한다.
→ 대사는 "복구가 잘 동작하는지 검증하는 최종 안전망"
→ 대사에서 불일치가 발견되면 = 복구 로직에 버그가 있다는 신호

쿠팡 관점: 대사 없는 결제 시스템은 없다.
정산 시점에 불일치가 발견되면 이미 늦다.
→ 1시간~일 단위 대사로 조기 감지.
```

### 22.6 대사 배치의 PG 부하 검증

```
[R1] PG ↔ Payment 대사:
  대상: 최근 24시간 PAID + FAILED 건
  현재 과제 규모: 하루 ~1000건 가정
  PG 조회: 1000건 × 1회 = 1000 API 호출
  Rate Limiter: 10 req/sec → 1000건 / 10 = 100초 (약 2분)
  → 1시간 주기 대비 2분 실행 → 부하율 3.3%

  쿠팡 규모: 하루 100만 건
  100만 / 10 req/sec = 100,000초 (약 28시간) → 불가
  → 프로덕션에서는 PG 정산 파일(bulk) 방식 사용
  → 현재 과제에서는 API 호출 방식으로 충분
```

### 22.7 → 05 반영 사항

| 반영 대상 | 내용 |
|---|---|
| Section 10 (복구/대사) | 대사 배치 3종 (R1/R2/R3) 설계 추가 |
| Section 15 (구현 계획) | Phase 5에 대사 배치 항목 추가 |
| Section 16 (패키지 구조) | ReconciliationScheduler 추가 |
| Section 17 (의존성) | reconciliation_mismatch 테이블 DDL |

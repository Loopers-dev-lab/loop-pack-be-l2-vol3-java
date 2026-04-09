Kafka 오프셋 전략 — 수동 커밋, At-Least-Once, 그리고 DLQ가 동작하지 않던 버그

> 이 파일은 블로그 글과 PR 설명에 사용할 소재 정리다.

---

## 배경: 오프셋 관리가 중요한 이유

Kafka에서 "어디까지 읽었는가"를 추적하는 오프셋은 컨슈머가 직접 관리한다. 브로커는 모른다. 이 설계 덕분에 Kafka는 높은 처리량을 유지하지만, 오프셋을 잘못 관리하면 메시지 유실이나 중복 처리가 발생한다.

우리 시스템에는 두 가지 Consumer가 있다:

| Consumer | 토픽 | 실패 시 허용 수준 |
|---|---|---|
| MetricsConsumer | catalog-events, order-events | 유실 허용 (배치 보정) |
| CouponIssueConsumer | coupon-issue-requests | **유실 불허** (선착순 쿠폰) |

같은 시스템이지만 실패 시 허용 수준이 다르다. 이 차이가 오프셋 커밋 전략에 직접적으로 영향을 준다.

---

## 설계 결정 1: 수동 커밋 (enable-auto-commit=false + AckMode.MANUAL)

### 자동 커밋의 문제

자동 커밋(`enable.auto.commit=true`)은 `auto.commit.interval.ms`(기본 5초) 주기로 커밋한다. 메시지를 poll 했지만 아직 처리하지 않은 시점에 커밋이 일어날 수 있다. 이 상태에서 컨슈머가 죽으면 메시지가 유실된다.

```
poll() → 3000건 수신 → [자동 커밋 발생] → 1500건째 처리 중 crash
→ 재시작 시 커밋된 오프셋부터 읽음 → 1500건 유실
```

### 우리의 선택

```yaml
consumer:
  properties:
    enable-auto-commit: false
listener:
  ack-mode: manual
```

모든 Factory에서 `AckMode.MANUAL` 적용. 비즈니스 로직이 완료된 후 명시적으로 `ack.acknowledge()`를 호출해야만 오프셋이 커밋된다.

### 라이팅 포인트

"자동 커밋은 편리하지만, 편리함이 안전을 보장하지는 않는다. 메시지 유실이 허용되지 않는 도메인에서는 수동 커밋 외에 선택지가 없다."

---

## 설계 결정 2: Consumer별 커밋 + 실패 처리 전략 분리

### MetricsConsumer: catch-and-continue + 배치 보정

```java
for (ConsumerRecord<String, byte[]> record : records) {
    try {
        tx.executeWithoutResult(status -> processRecord(record));
    } catch (Exception e) {
        log.error("처리 실패", e);  // 실패한 레코드는 스킵
    }
}
ack.acknowledge();  // 배치 전체 ack
```

실패한 레코드를 스킵하고 전체 배치를 ack한다. 이건 의도된 설계다:
- 집계 데이터는 즉시 정확하지 않아도 된다
- MetricsReconcile 배치가 주기적으로 정합성을 보정한다
- 하나의 실패 레코드 때문에 나머지 2,999건이 재처리되는 건 비효율적이다

### CouponIssueConsumer: 예외 전파 + DLQ

```java
public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(status -> processRecord(record));
    ack.acknowledge();
}
```

예외가 발생하면 `ack.acknowledge()`에 도달하지 못한다. 예외는 Spring Kafka의 `DefaultErrorHandler`로 전파되어:

1. `FixedBackOff(1000L, 3)` — 1초 간격으로 3회 재시도
2. 재시도 모두 실패 시 `DeadLetterPublishingRecoverer`가 `coupon-issue-requests.DLT` 토픽으로 전송
3. DLT 메시지는 운영자 확인 후 재처리

### 이전 버그: DLQ가 동작하지 않았던 이유

초기 구현에서는 CouponIssueConsumer도 MetricsConsumer와 동일한 패턴을 사용했다:

```java
// 버그가 있던 코드
try {
    tx.executeWithoutResult(status -> processRecord(record));
} catch (Exception e) {
    log.error("처리 실패", e);  // 예외를 삼킴
}
ack.acknowledge();  // 항상 ack → DLQ 도달 불가
```

`DefaultErrorHandler + DeadLetterPublishingRecoverer`를 Factory에 설정했지만, `consume()` 메서드 내부에서 예외를 catch하고 ack까지 호출하므로 ErrorHandler에 예외가 전파되지 않았다. DLQ 설정이 사실상 죽은 코드였다.

실패 시 흐름:
```
DB 에러 → TransactionTemplate 롤백 → catch에서 로그만 → ack → 오프셋 커밋
→ 메시지 재전달 불가, coupon_issue_request는 PENDING으로 영구 방치
```

### 수정 후 흐름

```
DB 에러 → TransactionTemplate 롤백 → 예외 전파 → DefaultErrorHandler
→ 1초 후 재시도 (최대 3회) → 여전히 실패 → DLT 토픽으로 전송
→ 오프셋 커밋 → 다음 메시지 처리 계속
```

### 라이팅 포인트

"DLQ를 설정했다고 동작하는 게 아니다. 예외가 ErrorHandler까지 전파되는 경로가 확보되어야 한다. try-catch로 예외를 삼키면 아무리 정교한 에러 핸들링 체인도 무용지물이 된다."

"같은 시스템 안에서도 Consumer마다 실패 허용 수준이 다르다. 집계 데이터의 실패와 쿠폰 발급의 실패는 비즈니스 임팩트가 다르고, 그 차이가 코드 구조에 반영되어야 한다."

---

## 발견 및 수정: auto.offset.reset 설정 충돌

### 문제

kafka.yml에 같은 Kafka 속성이 두 곳에 선언되어 있었다:

```yaml
# 전역 properties
properties:
    auto:
      offset.reset: latest       # ← latest

# consumer 전용
consumer:
  auto-offset-reset: earliest    # ← earliest
```

Spring Boot에서 consumer 전용이 전역을 오버라이드하므로 `earliest`가 적용되지만, 의도와 다른 값이 혼재하면:
- 코드 리뷰 시 어느 값이 적용되는지 혼란
- Spring Boot 버전 업그레이드 시 merge 순서 변경 리스크
- 죽은 설정이 남아있으면 "이게 왜 있지?" 질문을 유발

### 수정

전역 properties에서 `offset.reset: latest` 제거. consumer 전용 `auto-offset-reset: earliest`만 유지.

### 왜 earliest인가

우리 시스템은 새 Consumer Group 배포 시 **과거 메시지부터 처리**해야 한다:
- MetricsConsumer: 기존 이벤트를 모두 집계해야 product_metrics가 정확
- CouponIssueConsumer: 발급 요청이 누락되면 사용자 불만

`latest`는 "현재 시점 이후"만 처리하므로, 배포 직전까지 쌓인 메시지를 모두 유실한다. `earliest`는 대량 과거 메시지 처리 부담이 있지만, INSERT-first 멱등 패턴으로 중복을 방지하므로 안전하다.

### 라이팅 포인트

"설정 파일에 같은 속성이 두 곳에 다른 값으로 존재하면, 현재 동작이 맞더라도 시한폭탄이다. 설정은 하나의 진실만 가져야 한다."

---

## At-Least-Once와 멱등성의 관계

### 오프셋 커밋 타이밍과 메시지 보장 수준

| 시나리오 | MetricsConsumer | CouponIssueConsumer |
|---|---|---|
| 정상 처리 | exactly-once (멱등) | exactly-once (멱등) |
| 처리 중 crash (ack 전) | at-least-once → 멱등 스킵 | at-least-once → 멱등 스킵 |
| DB 에러로 처리 실패 | skip + ack (best-effort) | 재시도 3회 → DLQ |
| 리밸런싱으로 재전달 | at-least-once → 멱등 스킵 | at-least-once → 멱등 스킵 |

### 멱등 패턴이 없으면

at-least-once는 "최소 한 번 처리"를 보장하지만, "정확히 한 번"은 보장하지 않는다. 멱등 패턴 없이 at-least-once를 사용하면:
- 좋아요 수가 중복 증가
- 쿠폰이 중복 발급
- 주문 집계가 뻥튀기

우리의 INSERT IGNORE event_handled 패턴은 "이미 처리한 이벤트인가?"를 DB 레벨에서 확인하여, at-least-once 전달 + exactly-once 처리를 달성한다.

### 라이팅 포인트

"Kafka의 메시지 보장은 '전달(delivery)' 관점이다. at-least-once delivery가 at-least-once processing이 되지 않으려면, 컨슈머 측 멱등성이 필수다. 전달 보장과 처리 보장은 다른 레이어의 문제다."

---

## 전체 오프셋 전략 요약

```
┌──────────────────────────────────────────────────────┐
│                  오프셋 관리 전략                       │
├──────────────────────────────────────────────────────┤
│  [공통]                                               │
│  ├── enable-auto-commit: false                       │
│  ├── ack-mode: MANUAL                                │
│  ├── auto-offset-reset: earliest                     │
│  └── isolation.level: read_committed                 │
│                                                      │
│  [MetricsConsumer — best-effort]                     │
│  ├── 실패 시: catch + log + skip                      │
│  ├── 전체 배치 ack                                    │
│  ├── 보정: MetricsReconcile 배치                      │
│  └── 보장 수준: at-most-once (실패 시) + 배치 보정     │
│                                                      │
│  [CouponIssueConsumer — 유실 불허]                    │
│  ├── 실패 시: 예외 전파 → ErrorHandler                │
│  ├── 재시도: FixedBackOff(1초 × 3회)                  │
│  ├── 최종 실패: DLT 토픽 전송                         │
│  └── 보장 수준: at-least-once + 멱등                  │
└──────────────────────────────────────────────────────┘
```

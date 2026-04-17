Kafka 리밸런싱 전략 — 이커머스 이벤트 파이프라인에 적용한 설계와 근거

> 이 파일은 블로그 글과 PR 설명에 사용할 소재 정리다.

---

## 배경: 리밸런싱이 왜 중요한가

Kafka Consumer Group에서 컨슈머가 추가/제거되면 파티션 재할당(리밸런싱)이 발생한다. 리밸런싱 동안 메시지 소비가 중단되므로, 대규모 트래픽 환경에서는 이 중단 시간이 곧 메시지 적체로 이어진다.

우리 시스템에는 성격이 다른 두 가지 Consumer가 있다:

| Consumer | 토픽 | 특성 |
|---|---|---|
| MetricsConsumer | catalog-events, order-events | 집계용, 순서 무관, 대량 배치 처리 |
| CouponIssueConsumer | coupon-issue-requests | 선착순 발급, 순서 중요, 건별 처리 |

이 두 Consumer에 동일한 리밸런싱 설정을 적용하는 건 맞지 않다. 처리 특성이 다르면 리밸런싱 트리거 조건도 달라야 한다.

---

## 설계 결정 1: Cooperative Sticky Assignor 명시

### 문제

Kafka의 기본 파티션 할당 전략인 Round Robin(Eager)은 리밸런싱 시 **모든 컨슈머의 파티션을 회수한 뒤 재할당**한다. 이 동안 전체 Consumer Group이 멈추는 Stop-the-world가 발생한다.

### 결정

`CooperativeStickyAssignor`를 명시적으로 설정했다.

```yaml
# kafka.yml
consumer:
  properties:
    partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

### 근거

- Cooperative 프로토콜은 **변경이 필요한 파티션만** 재할당한다. 나머지 파티션은 기존 컨슈머가 계속 처리한다.
- Kafka 3.x+에서 기본값이긴 하지만, 버전 업그레이드 시 동작 변경 리스크를 방지하기 위해 명시한다.
- 운영 환경에서 "왜 이 전략인가?"를 코드만 보고 파악할 수 있어야 한다.

### 트레이드오프

Cooperative Sticky는 리밸런싱이 2단계(revoke → assign)로 나뉘어 전체 시간이 약간 더 길 수 있다. 하지만 **소비 중단 없이** 진행되므로, 처리량 관점에서는 이점이 크다.

---

## 설계 결정 2: Consumer 특성별 max.poll.interval.ms 분리

### 문제

`max.poll.interval.ms`는 "두 번의 poll() 사이 최대 허용 시간"이다. 이 시간을 초과하면 브로커가 해당 컨슈머를 죽은 것으로 판단하고 리밸런싱을 시작한다. 하지만 두 Consumer의 처리 시간이 근본적으로 다르다.

### 결정

| 설정 | BATCH_LISTENER (MetricsConsumer) | SINGLE_LISTENER (CouponIssueConsumer) |
|---|---|---|
| max.poll.records | 3,000 | 1 |
| max.poll.interval.ms | **2분** | **3분** |
| session.timeout.ms | 60초 | 60초 |
| heartbeat.interval.ms | 20초 (session의 1/3) | 20초 |

### 산술 근거

**MetricsConsumer (2분)**:
- 3,000건 × UPSERT 1건당 ~1ms = 최대 3초
- 2분(120초) = 40배 마진

**CouponIssueConsumer (3분)**:
- 정상 처리: JSON 파싱 + INSERT IGNORE + CAS UPDATE + INSERT + UPDATE = ~10ms
- DLQ 재시도: FixedBackOff(1초 × 3회) = 3초
- 커넥션 풀 고갈 최악 케이스: HikariCP connectionTimeout 30초 + 재시도 3초 = ~33.5초
- 3분(180초) = 최악 케이스 대비 **5배 마진**

### 왜 10분이 아니라 3분인가

초기에는 CouponIssueConsumer의 max.poll.interval.ms를 10분으로 설정했다. 하지만 검토 결과:

- 10분은 정상 처리 대비 60,000배 마진 — 과도하다
- 컨슈머가 실제로 stuck 되었을 때(deadlock, 무한루프), **10분간 감지 불가**
- 선착순 쿠폰 플래시 세일 기준 100 req/s × 600초 = **6만 건 처리 지연**
- 3분으로 줄이면 stuck 감지 시간 1/3로 단축, 커넥션 풀 고갈 최악 케이스에도 5배 마진 확보

**교훈**: 타임아웃 값은 "넉넉하게"가 아니라 "최악 케이스의 N배"로 설정해야 한다. 너무 짧으면 불필요한 리밸런싱, 너무 길면 장애 감지 지연. 산술적 근거 없이 설정하면 양쪽 다 위험하다.

---

## 설계 결정 3: Static Membership (group.instance.id)

### 문제

컨슈머가 재시작되면 브로커는 새로운 멤버로 인식하여 리밸런싱을 트리거한다. Rolling deployment 시 N개 인스턴스가 순차 재시작되면 N번의 리밸런싱이 발생한다.

### 결정

`group.instance.id`를 호스트명 기반으로 설정한다.

```java
// KafkaConfig.java
@Value("${HOSTNAME:local}")
private String hostname;

// BATCH_LISTENER
consumerConfig.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, hostname + "-batch");

// SINGLE_LISTENER
consumerConfig.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, hostname + "-single");
```

### 효과

- 컨슈머 재시작 시 `session.timeout.ms`(60초) 이내에 복귀하면 **리밸런싱 없이 기존 파티션 유지**
- Rolling deployment 시 불필요한 리밸런싱 방지
- Kubernetes 환경에서 `HOSTNAME`은 Pod 이름으로 자동 설정됨

### 주의점

- `session.timeout.ms`를 초과하는 재시작은 여전히 리밸런싱 발생
- 인스턴스가 영구 제거될 때는 해당 `group.instance.id`의 파티션이 `session.timeout.ms` 후에야 재할당됨

---

## 리밸런싱 발생 시 안전성: INSERT-first 멱등 패턴

리밸런싱으로 파티션이 재할당되면 **이미 처리했지만 ack 전인 메시지가 재처리**될 수 있다. 이 중복 소비에 대한 안전장치가 필요하다.

### MetricsConsumer (BATCH, MANUAL ack)

```
3,000건 처리 중 1,500건째에 리밸런싱 발생
→ ack.acknowledge() 호출 전이므로 3,000건 전체 재처리
→ INSERT IGNORE event_handled로 1,500건은 멱등 스킵
→ 나머지 1,500건만 실제 처리
→ UPSERT product_metrics이므로 값이 꼬이지 않음
```

### CouponIssueConsumer (SINGLE, MANUAL ack)

```
쿠폰 발급 처리 중 리밸런싱 발생
→ ack 전이므로 해당 메시지 재처리
→ INSERT IGNORE event_handled로 중복 감지 → 스킵
→ 이미 발급된 쿠폰이 다시 발급되지 않음
```

핵심은 **"리밸런싱을 막는 것"이 아니라 "리밸런싱이 발생해도 비즈니스가 깨지지 않는 구조"**를 만드는 것이다.

---

## 전체 설정 요약

```
┌──────────────────────────────────────────────────────────────┐
│                    Kafka Consumer 설정                        │
├──────────────────────────────────────────────────────────────┤
│  [공통]                                                       │
│  ├── partition.assignment.strategy: CooperativeStickyAssignor│
│  ├── isolation.level: read_committed                         │
│  ├── enable-auto-commit: false                               │
│  └── ack-mode: MANUAL                                        │
│                                                              │
│  [BATCH_LISTENER — MetricsConsumer]                          │
│  ├── max.poll.records: 3,000                                 │
│  ├── max.poll.interval.ms: 120,000 (2분)                     │
│  ├── session.timeout.ms: 60,000 (1분)                        │
│  ├── heartbeat.interval.ms: 20,000 (20초)                    │
│  ├── group.instance.id: ${HOSTNAME}-batch                    │
│  ├── concurrency: 3                                          │
│  └── 멱등: INSERT IGNORE event_handled + UPSERT              │
│                                                              │
│  [SINGLE_LISTENER — CouponIssueConsumer]                     │
│  ├── max.poll.records: 1                                     │
│  ├── max.poll.interval.ms: 180,000 (3분)                     │
│  ├── session.timeout.ms: 60,000 (1분)                        │
│  ├── heartbeat.interval.ms: 20,000 (20초)                    │
│  ├── group.instance.id: ${HOSTNAME}-single                   │
│  ├── concurrency: 1                                          │
│  └── 멱등: INSERT IGNORE + CAS UPDATE + DLQ                  │
└──────────────────────────────────────────────────────────────┘
```

---

## 라이팅 포인트

1. **"리밸런싱을 막는 것 vs 리밸런싱에 안전한 것"** — 분산 시스템에서 장애를 완전히 막을 수는 없다. 막으려 하기보다 발생해도 안전한 구조를 만드는 게 Resilience다.

2. **"타임아웃은 감으로 정하지 않는다"** — max.poll.interval.ms를 10분으로 잡으면 안전해 보이지만, stuck 컨슈머를 10분간 방치하는 것과 같다. 최악 케이스를 산출하고, 적절한 마진 배수를 곱하는 게 엔지니어링이다.

3. **"같은 시스템 안에서도 Consumer마다 전략이 달라야 한다"** — 집계용 Consumer와 발급용 Consumer에 같은 타임아웃을 적용하는 건 "모든 API에 동일한 Circuit Breaker 임계값을 적용하는 것"과 같다. 도메인 특성이 다르면 인프라 설정도 달라야 한다.

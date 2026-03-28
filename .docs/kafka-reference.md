# Apache Kafka 완전 정리

> 이 문서는 Kafka의 핵심 개념부터 물리적 저장 구조, Spring Boot 연동, 운영 전략까지를 하나의 문서로 정리한 레퍼런스입니다.
> 커머스 시스템(주문/결제/재고) 맥락을 기준으로 작성되었습니다.

---

## 1. Kafka는 왜 존재하는가

### 1-1. Kafka가 없는 세계의 문제

서비스 간 통신에서 가장 원시적인 방법은 동기 HTTP 호출이다.
주문 서비스가 재고 서비스를 호출하고, 결제 서비스를 호출하고, 알림 서비스를 호출하는 구조에서 근본적으로 세 가지 결합(coupling)이 발생한다.

**시간적 결합**: A가 B에게 알려주려면 B가 지금 살아있어야 한다. B가 점검 중이면 A도 실패한다.

**공간적 결합**: A가 B의 주소(endpoint)를 알아야 한다. 알려줄 대상이 늘어날 때마다 A의 코드가 변경된다.

**속도 결합**: A → B → C → D 순차 호출 시 총 응답시간은 B+C+D의 합이다. 가장 느린 서비스가 전체를 지배한다.

```
// Kafka 없이 동기 호출하는 구조의 전형적 문제
@Transactional
public void createOrder(OrderRequest req) {
    orderRepository.save(order);           // 10ms
    inventoryService.deduct(req);          // 50ms (다른 서비스)
    pgClient.requestPayment(req);          // 2~5초 (외부 PG)
    pointService.deduct(req);              // 30ms (또 다른 서비스)
    notificationService.send(req);         // 1~3초 (외부 API)
    // → 총 3~8초 동안 DB 커넥션 점유
    // → 5번에서 실패하면 1~4를 전부 롤백해야 하지만
    //   3번 PG는 이미 돈이 빠진 상태라 롤백 불가
}
```

### 1-2. Kafka의 본질: 중간에 영구적 로그를 둔다

Kafka가 하는 일의 본질은 단순하다. A와 B 사이에 **영구적 로그**를 끼워넣는 것이다.

A는 "주문이 생겼다"를 로그에 한 줄 쓰고 끝낸다. B는 자기 속도로 그 로그를 읽어간다.
이것만으로 시간적·공간적·속도 결합이 전부 깨진다.

```
// Kafka를 도입한 구조
@Transactional
public void createOrder(OrderRequest req) {
    orderRepository.save(order);                    // 비즈니스 로직
    outboxRepository.save(new OutboxEvent(...));    // 같은 DB에 INSERT 한 줄
}
// → 트랜잭션 끝. 수 밀리초.
// → 재고, 결제, 포인트, 알림은 각 컨슈머가 비동기 처리
```

### 1-3. 도입 시점 판단 기준

Kafka는 "좋아 보여서" 도입하는 게 아니라, 기존 구조에서 구체적 고통이 발생할 때 도입한다.

| 신호 | 상황 | Kafka가 해결하는 것 |
|------|------|---------------------|
| 장애 전파 | 알림 서비스가 죽었더니 주문 API도 500 | 서비스 간 결합 제거 |
| N:M 연결 폭발 | 주문 이벤트를 4곳에 알려야 하고, 새 수신자 추가마다 코드 수정 | pub/sub 모델로 발행자-구독자 분리 |
| 처리량 불균형 | 주문 3,000건/s인데 PG 호출은 500건/s | 버퍼링 + 컨슈머 독립 확장 |
| 데이터 재처리 요구 | 정산 로직 버그로 3일치를 다시 돌려야 함 | offset 되감기로 재처리 |

반대로, 서비스가 모놀리식이고 수신자가 1~2개뿐이면 Kafka는 과잉 엔지니어링이다.
**"비동기 분리의 이점 > Kafka 운영 비용"** 일 때 도입한다.

> 💡 **더 파보면 좋을 것**: 이벤트 드리븐 아키텍처(EDA)와 Choreography vs Orchestration 패턴의 트레이드오프.
> 고민해볼 것: "우리 시스템에서 동기 호출로 인한 장애 전파가 실제로 발생한 적 있는가?"

---

## 2. 핵심 개념

### 2-1. Broker

Kafka 서버 프로세스 한 대를 broker라고 부른다. 여러 대를 묶으면 cluster가 된다.

```
Kafka Cluster
┌─────────────┬─────────────┬─────────────┐
│  Broker 1   │  Broker 2   │  Broker 3   │
│ :9092       │ :9093       │ :9094       │
└─────────────┴─────────────┴─────────────┘
```

브로커는 메시지를 실제로 디스크에 저장하고 서빙하는 물리적 단위이다.
클러스터 내 브로커 간 조율은 과거에는 ZooKeeper가 담당했으나, Kafka 3.x부터는 KRaft(Kafka Raft) 모드로 자체 관리한다.

> 📎 공식 문서: https://kafka.apache.org/documentation/#brokerconfigs

### 2-2. Topic

메시지의 **논리적 채널**이다. `order-events`, `payment-events` 같은 이름을 붙여서 메시지를 분류한다.
Topic은 물리적 실체가 아니라 "이 파티션들은 이 이름으로 묶인다"는 **메타데이터**이다.

```bash
# 토픽 생성
kafka-topics.sh --create \
  --topic order-events \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

```bash
# 토픽 상세 정보 확인
kafka-topics.sh --describe --topic order-events
# 출력:
# Topic: order-events   Partitions: 3   Replication: 3
#   Partition 0  →  Leader: Broker 1,  Replicas: [1, 2, 3]
#   Partition 1  →  Leader: Broker 2,  Replicas: [2, 3, 1]
#   Partition 2  →  Leader: Broker 3,  Replicas: [3, 1, 2]
```

> 📎 공식 문서: https://kafka.apache.org/documentation/#topicconfigs

### 2-3. Partition

토픽을 **물리적으로 쪼갠 단위**이다. 각 파티션은 순서가 보장되는 append-only 로그이다.

핵심 특성:
- 메시지 하나는 **정확히 하나의 파티션에만** 들어간다 (복제가 아니라 분배)
- 파티션 내에서 순서가 보장된다 (파티션 간에는 보장되지 않음)
- 파티션 수 = 최대 병렬 소비 단위
- 파티션 수는 늘릴 수 있지만 줄일 수 없다

```
Topic: order-events (3 partitions)

Partition 0: [msg0][msg3][msg6][msg9]  ...  → append
Partition 1: [msg1][msg4][msg7][msg10] ...  → append
Partition 2: [msg2][msg5][msg8][msg11] ...  → append
             ← offset 순서대로 쌓임 →
```

파티션들은 여러 브로커에 분산 저장된다.
브로커 3대에 파티션 12개면, 각 브로커가 파티션 4개씩 나눠 들고 있다.

```
┌─────────────────────────────────────────────┐
│            Topic: order-events              │
├───────────────┬───────────────┬─────────────┤
│   Broker 1    │   Broker 2    │  Broker 3   │
│  P0 (leader)  │  P1 (leader)  │ P2 (leader) │
│  P2 (replica) │  P0 (replica) │ P1 (replica)│
└───────────────┴───────────────┴─────────────┘
```

**파티션을 여러 개 두는 이유:**
1. **처리량 확장**: 파티션 수만큼 컨슈머를 병렬로 붙일 수 있다
2. **쓰기 분산**: leader가 서로 다른 브로커에 있으니 쓰기 부하가 분산된다
3. **장애 범위 축소**: 브로커 하나가 죽어도 해당 파티션만 영향을 받는다

> 💡 **더 파보면 좋을 것**: 파티션 수 결정 공식. 일반적으로 `(목표 처리량) / (컨슈머 1개의 처리량)`으로 산정한다. 실무에서는 성장을 감안해 넉넉하게 잡되(12, 24, 36 등), 너무 많으면 리밸런싱 시간이 길어지는 트레이드오프가 있다.

### 2-4. Producer

메시지를 토픽에 보내는 클라이언트이다.

핵심 동작:
1. 메시지에 **partition key**를 지정할 수 있다
2. `hash(key) % 파티션 수`로 어떤 파티션에 보낼지 결정된다
3. 같은 키는 항상 같은 파티션으로 간다 → **해당 키에 대한 순서 보장**
4. key가 null이면 라운드로빈으로 분배된다

```java
// Spring Boot에서 프로듀서 사용
kafkaTemplate.send(
    "order-events",                              // topic
    order.getId().toString(),                     // partition key (orderId)
    toJson(new OrderCreatedEvent(order))          // value (메시지 본문)
);
// → 같은 orderId는 항상 같은 파티션 → 주문 이벤트 순서 보장
```

> 📎 공식 문서: https://kafka.apache.org/documentation/#producerconfigs

### 2-5. Consumer와 Consumer Group

**Consumer**는 토픽에서 메시지를 읽는 클라이언트이다.

**Consumer Group**은 같은 `group.id`를 가진 컨슈머들의 집합이다.
그룹 내에서 파티션은 1:1로 분배되고, 서로 다른 그룹은 같은 토픽을 독립적으로 소비한다.

```
Topic: order-events (3 partitions)
          P0        P1        P2
          │         │         │
    ┌─────┴────┐    │    ┌────┴─────┐
    ▼          │    ▼    │          ▼
┌────────────────────────────────────────┐
│  Consumer Group A (payment-service)    │
│  C1 ← P0    C2 ← P1    C3 ← P2       │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│  Consumer Group B (analytics-service)  │
│  C1 ← P0,P1       C2 ← P2            │
└────────────────────────────────────────┘
→ 두 그룹은 같은 토픽을 독립적으로 소비, 각자 offset 관리
```

**파티션 수와 컨슈머 수의 관계:**

| 상태 | 결과 |
|------|------|
| 컨슈머 < 파티션 | 한 컨슈머가 여러 파티션 담당 (동작하지만 부하 불균형) |
| 컨슈머 = 파티션 | 1:1 매핑, 최적 상태 |
| 컨슈머 > 파티션 | 남는 컨슈머는 유휴 상태 (리소스 낭비) |

**서버 하나에서도 병렬 처리가 가능하다:**
`@KafkaListener`의 `concurrency` 설정으로 JVM 내부에 여러 컨슈머 스레드를 생성할 수 있다.

```java
// 스레드 3개가 각각 파티션 하나씩 담당
@KafkaListener(topics = "order-events",
               groupId = "order-service",
               concurrency = "3")
public void onOrderEvent(OrderEvent event) {
    // 스레드-0 → P0 처리
    // 스레드-1 → P1 처리
    // 스레드-2 → P2 처리
}
```

트래픽이 더 늘면 같은 `group.id`로 서버를 추가 배포하면 된다. Kafka가 자동으로 파티션을 재분배(rebalance)한다.

> 📎 공식 문서: https://kafka.apache.org/documentation/#consumerconfigs

### 2-6. Offset

파티션 내 각 메시지의 **고유 순번**이다. 컨슈머는 "내가 어디까지 읽었는지"를 offset으로 추적한다.

핵심 특성:
- **컨슈머(그룹)가 관리**한다 (브로커가 아님)
- 각 컨슈머 그룹이 독립적으로 offset을 관리 → 재소비 가능
- offset을 되감으면(seek) 과거 메시지를 다시 읽을 수 있다

```
Partition 0:
[0][1][2][3][4][5][6]   ← offset

         ↑               ↑
   Group B offset=2    Group A offset=4
   (분석 서비스, 느림)   (결제 서비스, 빠름)
```

### 2-7. Segment

파티션을 **물리적 파일 단위로 쪼갠 것**이다.

파티션이 하나의 거대한 파일이면, 오래된 데이터 삭제 시 파일 중간을 잘라내야 해서 비용이 크다.
세그먼트로 나누면 오래된 세그먼트 파일을 통째로 삭제하면 된다.

```
order-events-0/              ← 파티션 디렉토리
├── 00000000000000000000.log         ← 세그먼트 0 (offset 0~4519)
├── 00000000000000000000.index
├── 00000000000000000000.timeindex
├── 00000000000000004520.log         ← 세그먼트 1 (offset 4520~8999)
├── 00000000000000004520.index
├── 00000000000000004520.timeindex
├── 00000000000000009000.log         ← 세그먼트 2 (active, 쓰기 중)
├── 00000000000000009000.index
└── 00000000000000009000.timeindex
```

- 파일 이름 = 해당 세그먼트의 시작 offset (baseOffset)
- 마지막 세그먼트 = **active segment** (현재 쓰기가 일어나는 곳)
- 기본 1GB(`log.segment.bytes`) 또는 7일(`log.roll.ms`)에 도달하면 새 세그먼트로 롤오버
- retention 정책에 따라 오래된 세그먼트를 통째로 삭제

### 2-8. Kafka Connect

코드 없이 설정만으로 **외부 시스템과 Kafka를 파이프라인으로 연결**하는 프레임워크이다.

- **Source Connector**: 외부 → Kafka (예: MySQL CDC → Kafka 토픽)
- **Sink Connector**: Kafka → 외부 (예: Kafka 토픽 → Elasticsearch)

커머스에서의 대표적 사용 예:
Debezium Source Connector로 주문 DB 변경을 Kafka로 스트리밍하고,
Sink Connector로 Elasticsearch(검색)이나 데이터 웨어하우스(분석)에 자동 동기화.

```json
{
  "name": "mysql-source",
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "database.hostname": "localhost",
  "database.server.name": "mydb",
  "table.include.list": "orders.order_table"
}
```

> 📎 공식 문서: https://kafka.apache.org/documentation/#connect

> 💡 **더 파보면 좋을 것**: Debezium의 동작 원리(MySQL binlog 기반 CDC), 그리고 Outbox 패턴을 Debezium Outbox Event Router로 구현하는 방법.

---

## 3. 메시지 처리 방식

### 3-1. Producer의 메시지 전송 과정

```
Producer
  │
  ├─ 1) Serializer: 객체 → byte[]
  ├─ 2) Partitioner: hash(key) % 파티션 수 → 파티션 결정
  ├─ 3) RecordAccumulator: 배치로 모음
  ├─ 4) Sender: 네트워크로 브로커에 전송
  │
  ▼
Broker (해당 파티션의 leader)
  ├─ 5) leader의 로컬 로그에 append
  ├─ 6) follower들이 leader로부터 복제 (fetch)
  └─ 7) acks 설정에 따라 OK 응답
```

### 3-2. ISR (In-Sync Replicas)

파티션의 replica 중 leader와 데이터가 **충분히 동기화된 replica의 집합**이다.

```
Partition 0 (replication-factor=3):
  Leader   (Broker 1) — offset 100까지 있음
  Follower (Broker 2) — offset 99까지 복제 → ISR에 포함 ✓
  Follower (Broker 3) — offset 82까지 복제 → ISR에서 탈락 ✗

  ISR = [Broker 1, Broker 2]
```

- leader가 죽으면 **ISR 안의 follower만** 새 leader로 승격 가능
- ISR 밖의 follower가 leader가 되면 미복제 메시지 유실 위험

### 3-3. acks 옵션

프로듀서가 메시지를 보낼 때 **"얼마나 확실한 저장 보장을 받을 것인가"**를 결정한다.

| acks | 동작 | 속도 | 안전성 | 용도 |
|------|------|------|--------|------|
| `0` | 응답 안 기다림 | 최고 | 유실 가능 | 로그, 메트릭 |
| `1` | leader 저장 확인 | 빠름 | 드물게 유실 | 일반 데이터 |
| `all(-1)` | ISR 전체 복제 확인 | 느림 | 유실 없음 | 결제, 주문 |

**`acks=all`은 반드시 `min.insync.replicas`와 세트로 설정한다.**

```properties
# 운영 권장 설정
acks=all
min.insync.replicas=2
replication-factor=3
```

`min.insync.replicas=2`면 ISR에 최소 2개 replica가 있어야 쓰기를 허용한다.
ISR이 1개로 줄면 쓰기를 거부해서 데이터 유실을 원천 차단한다.

> 📎 공식 문서: https://kafka.apache.org/documentation/#producerconfigs_acks

### 3-4. Replica와 브로커 수의 관계

`replication-factor=3`이면 같은 파티션의 복제본이 서로 다른 브로커 3대에 분산되어야 한다.
브로커가 2대뿐이면 토픽 생성 자체가 실패한다.

- 파티션 수와 브로커 수는 **독립적**이다. 브로커 3대에 파티션 12개 가능
- replica는 반드시 서로 다른 브로커에 배치된다 (같은 브로커에 leader+replica를 두면 장애 시 함께 소실)
- 개발 환경에서는 `replication-factor=1`로 브로커 1대로 운영 가능 (데이터 유실 감수)

### 3-5. 메시지 기록 포맷

Kafka 레코드(메시지) 하나에 기록되는 필드:

| 필드 | 크기 | 설명 |
|------|------|------|
| offset | int64 | 파티션 내 순번 |
| timestamp | int64 (ms) | 메시지 생성 시각 |
| key | byte[] | 파티션 라우팅 키 (nullable) |
| value | byte[] | 실제 페이로드 |
| headers | key-value[] | 메타데이터 (traceId 등) |
| CRC | int32 | 무결성 체크섬 |

key와 value는 `byte[]`이므로 JSON, Avro, Protobuf 등 직렬화 포맷은 프로듀서/컨슈머가 정한다.
Kafka 브로커 입장에서는 바이트 덩어리일 뿐이다.

```
실제 데이터 예시:
  offset: 42
  timestamp: 1711094400000 (2026-03-22T10:00:00)
  key: "order-1001"
  value: {"eventType":"ORDER_CREATED","orderId":1001,"amount":59000}
  headers: [("traceId", "abc-123")]
```

### 3-6. 프로듀서에서 컨슈머까지 전체 흐름 요약

```
Producer                       Kafka Cluster                    Consumer
   │                               │                               │
   │  1) send("order-events",      │                               │
   │     key="order-1001",         │                               │
   │     value={...})              │                               │
   │──────────────────────────────▶│                               │
   │                               │  2) hash("order-1001") % 3   │
   │                               │     = Partition 1              │
   │                               │                               │
   │                               │  3) Partition 1의 leader      │
   │                               │     (Broker 2)에 append       │
   │                               │                               │
   │                               │  4) ISR follower들이 복제     │
   │                               │                               │
   │  5) acks에 따라 OK 응답       │                               │
   │◀──────────────────────────────│                               │
   │                               │                               │
   │                               │  6) Consumer Group의          │
   │                               │     consumer가 poll()         │
   │                               │──────────────────────────────▶│
   │                               │                               │  7) 비즈니스 로직 처리
   │                               │                               │  8) offset 커밋
```

> 💡 **고민해볼 것**: 프로듀서의 `linger.ms`와 `batch.size`를 조정해서 배치 효율을 높이는 방법. 메시지를 하나씩 보내는 것보다 모아서 보내면 네트워크 왕복이 줄어든다.

---

## 4. 물리적 저장 구조 (Storage Internals)

### 4-1. 디렉토리 구조

```
/var/kafka-logs/                  ← log.dirs 설정
├── order-events-0/               ← 토픽-파티션 디렉토리
│   ├── 00000000000000000000.log       ← 메시지 본문 (FileRecords)
│   ├── 00000000000000000000.index     ← offset → byte position 매핑
│   ├── 00000000000000000000.timeindex ← timestamp → offset 매핑
│   ├── 00000000000000004520.log       ← 새 세그먼트
│   ├── 00000000000000004520.index
│   └── 00000000000000004520.timeindex
├── order-events-1/
└── order-events-2/
```

### 4-2. 핵심 클래스 구조

Kafka 소스코드의 스토리지 핵심 클래스들이다.
(경로: `storage/src/main/java/org/apache/kafka/storage/internals/log/`)

> 📎 소스코드: https://github.com/apache/kafka

#### LogSegment — 세그먼트의 실체

```java
public class LogSegment implements Closeable {
    private final FileRecords log;                    // .log 파일
    private final LazyIndex<OffsetIndex> offsetIndex;  // .index 파일
    private final LazyIndex<TimeIndex> timeIndex;      // .timeindex 파일
    private final long baseOffset;                     // 시작 offset
}
```

#### LogSegment.append() — 메시지 쓰기

```java
public void append(long largestOffset, long largestTimestamp,
                   long shallowOffsetOfMaxTimestamp, MemoryRecords records) {
    // 1) .log 파일 끝에 바이너리 레코드 append
    int appendedBytes = log.append(records);

    // 2) index.interval.bytes(기본 4096)만큼 쌓일 때마다
    //    .index에 sparse 엔트리 추가
    if (bytesSinceLastIndexEntry > indexIntervalBytes) {
        offsetIndex().append(largestOffset, physicalPosition);
        timeIndex().maybeAppend(largestTimestamp, largestOffset);
        bytesSinceLastIndexEntry = 0;
    }
    bytesSinceLastIndexEntry += appendedBytes;
}
```

#### OffsetIndex — offset으로 물리 위치 찾기

```java
public class OffsetIndex extends AbstractIndex {
    private static final int ENTRY_SIZE = 8;
    // 엔트리 하나: relative offset (4B) + physical position (4B)

    private MappedByteBuffer mmap;  // memory-mapped file

    public OffsetPosition lookup(long targetOffset) {
        // mmap 위에서 binary search
        // → targetOffset 이하인 가장 큰 엔트리의 position 반환
    }
}
```

- relative offset을 쓰는 이유: baseOffset으로부터의 차이만 저장하면 4바이트(int)로 충분. 절대 offset이면 8바이트(long)가 필요해서 엔트리 크기가 2배로 커진다.

#### TimeIndex — timestamp로 offset 찾기

```java
public class TimeIndex extends AbstractIndex {
    private static final int ENTRY_SIZE = 12;
    // 엔트리 하나: timestamp (8B) + relative offset (4B)

    public TimestampOffset lookup(long targetTimestamp) {
        // binary search로 targetTimestamp 이하인
        // 가장 큰 timestamp의 offset을 반환
    }
}
```

`--to-datetime`으로 되감기할 때의 경로:
1. `TimeIndex.lookup(15:00:00)` → offset 2450
2. `OffsetIndex.lookup(2450)` → position 98304
3. `FileRecords.read(98304)` → 메시지 반환

#### LogSegments — 세그먼트 컬렉션 관리

```java
public class LogSegments {
    // key = baseOffset, value = LogSegment
    private final ConcurrentSkipListMap<Long, LogSegment> segments;

    public Optional<LogSegment> floorSegment(long offset) {
        // offset 이하인 가장 큰 baseOffset을 가진 세그먼트
        // 예: segments = {0: seg0, 4520: seg1, 9000: seg2}
        //     floorSegment(5000) → seg1 (baseOffset=4520)
    }

    public LogSegment activeSegment() {
        // segments.lastEntry() — 가장 마지막 세그먼트
    }
}
```

`ConcurrentSkipListMap`을 사용하는 이유:
- baseOffset으로 정렬된 상태에서 `floorEntry()`를 O(log n)에 수행 가능
- 읽기/쓰기 동시 접근에 대해 lock-free에 가까운 성능

### 4-3. 2단계 탐색 구조

컨슈머가 특정 offset을 요청하면 아래 2단계로 찾는다:

```
"offset 42를 읽고 싶다"

[1단계] 어떤 세그먼트 파일?
        LogSegments (ConcurrentSkipListMap)
        floorEntry(42) → baseOffset=0인 세그먼트 선택
        → Skip List 탐색 O(log n), 대상: 세그먼트 수 (수십 개)

[2단계] 그 파일의 몇 번째 바이트?
        OffsetIndex (.index 파일)
        lookup(42) → binary search
        → "offset 40은 .log 파일의 16480 바이트 위치"
        → position 16480부터 순차 스캔 → offset 42 발견
        → 대상: 인덱스 엔트리 수 (최대 수십만 개)
```

### 4-4. Binary Search 구현 — AbstractIndex.indexSlotRangeFor()

Kafka의 `AbstractIndex` 클래스에 바이너리 서치가 직접 구현되어 있다.
mmap은 바이너리 서치와 무관하며, 파일을 메모리에 매핑해서 배열처럼 접근할 수 있게 해주는 역할일 뿐이다.

```scala
// AbstractIndex.scala — 실제 Kafka 소스코드
private def indexSlotRangeFor(idx: ByteBuffer, target: Long,
                              searchEntity: IndexSearchType): (Int, Int) = {
    if (_entries == 0) return (-1, -1)

    // 바이너리 서치 직접 구현
    def binarySearch(begin: Int, end: Int): (Int, Int) = {
        var lo = begin
        var hi = end
        while (lo < hi) {
            val mid = (lo + hi + 1) >>> 1  // unsigned shift로 오버플로 방지
            val found = parseEntry(idx, mid)
            val compareResult = compareIndexEntry(found, target, searchEntity)
            if (compareResult > 0) hi = mid - 1
            else if (compareResult < 0) lo = mid
            else return (mid, mid)
        }
        (lo, if (lo == _entries - 1) -1 else lo + 1)
    }

    // ★ Kafka만의 최적화: warm/cold 영역 분리
    val firstHotEntry = Math.max(0, _entries - 1 - _warmEntries)
    if (compareIndexEntry(parseEntry(idx, firstHotEntry), target, searchEntity) < 0) {
        return binarySearch(firstHotEntry, _entries - 1)  // warm에서만 검색
    }
    // warm에 없으면 cold에서 검색 ...
}
```

- `(lo + hi + 1) >>> 1`: unsigned right shift로 lo+hi가 int 범위를 넘길 때 오버플로를 방지한다. Java의 `Arrays.binarySearch`에서도 한때 이 버그가 있었던 유명한 케이스이다.

### 4-5. Warm/Cold 영역 분리 — 8192 바이트의 의미

`_warmEntries`는 8192 바이트 기준으로 계산된다.
- OffsetIndex: 8192 / 8 (엔트리 크기) = **1024개** 엔트리
- TimeIndex: 8192 / 12 = **682개** 엔트리

```
.index 파일 전체:
[엔트리 0 ··· 엔트리 N-1024 ··· 엔트리 N]
|───── cold 영역 ─────|──── warm 영역 (뒤쪽 1024개) ────|

탐색 순서:
1) target이 warm 영역에 있으면 → warm 안에서만 binary search (page cache hit 확실)
2) target이 warm보다 작으면 → cold 영역에서 binary search (page fault 가능)
```

**8192 바이트 = OS page 2장 (리눅스 기본 page size 4KB)**

이 크기인 이유:
- .index 파일의 맨 뒤쪽 2 page는 가장 최근에 쓰인 영역이라 page cache에 거의 100% 상주
- 대부분의 컨슈머는 최신 데이터를 읽기 때문에 warm 영역에서 hit될 확률이 높음
- warm 영역 1024개 엔트리 × `index.interval.bytes` 4KB = **약 4MB** 분량의 .log 데이터를 커버
- 실시간으로 따라가는 컨슈머는 거의 확실하게 이 범위 안에 있음

warm/cold 분리 없이 전체를 한 번에 이분탐색하면, mid가 파일 중간(cold 영역)을 찍을 때마다 page fault가 발생할 수 있다. 알고리즘적 복잡도는 동일한 O(log n)이지만, **실제 디스크 I/O 횟수를 줄이기 위한 캐시 친화적 최적화**이다.

### 4-6. 세그먼트 롤오버 조건

`LogSegment.shouldRoll()` 메서드에서 결정된다:

```java
public boolean shouldRoll(RollParams rollParams) {
    boolean reachedMaxSize = size() > rollParams.maxSegmentBytes - messagesSize;
    // → log.segment.bytes (기본 1GB) 초과

    boolean reachedMaxTime = timeWaitedForRoll() > rollParams.maxSegmentMs;
    // → log.roll.ms (기본 7일) 초과

    boolean indexFull = offsetIndex().isFull() || timeIndex().isFull();
    // → segment.index.bytes (기본 10MB) 도달

    return reachedMaxSize || reachedMaxTime || indexFull;
}
```

롤오버 시:
1. 현재 active segment의 index를 flush & trim
2. 새 `LogSegment(baseOffset = nextOffset)` 생성
3. segments map에 추가 → 새 세그먼트가 active segment가 됨

### 4-7. Kafka가 빠른 이유: Page Cache + Zero-Copy + Sparse Index

#### Page Cache (OS 기능)

디스크에서 읽은 데이터를 커널이 메모리에 자동 캐싱하는 OS 기능이다.

```
일반적인 접근:
  디스크 → JVM 힙 캐시 → 컨슈머에 전달 (GC 부담, 메모리 이중 사용)

Kafka의 접근:
  디스크 → OS page cache → 컨슈머에 전달 (GC 없음, OS가 관리)
```

Kafka는 JVM 힙에 메시지를 캐싱하지 않고, OS page cache에 전적으로 위임한다.
프로듀서가 append한 데이터가 page cache에 올라가고, 컨슈머가 바로 뒤따라 읽으면 page cache에서 hit된다.
실시간 컨슈머는 **사실상 디스크를 전혀 안 건드린다**.

#### Zero-Copy (sendfile 시스템콜)

```
일반적인 전송 (4번 복사):
  디스크 → 커널 버퍼 → 애플리케이션 버퍼(JVM) → 소켓 버퍼 → NIC

Zero-Copy:
  디스크 → 커널 버퍼(page cache) → NIC  (JVM을 거치지 않음)
```

Kafka는 메시지를 가공 없이 그대로 전달하므로 `FileChannel.transferTo()`를 사용한다.
리눅스에서는 `sendfile()` 시스템콜로 변환되어, 커널 내부에서 page cache → NIC로 직접 DMA 전송이 일어난다.

효과: CPU 사용량 감소, context switch 제거, 메모리 대역폭 절약.

#### Sparse Index

매 메시지마다 인덱스에 기록하는 게 아니라, `index.interval.bytes`(기본 4KB)만큼 쌓일 때마다 한 번씩 기록한다.

```
Dense index: 100만 메시지 × 8B = 8MB (인덱스가 커서 page cache 압박)
Sparse index: 1GB / 4KB × 8B = ~2MB (작아서 전체가 page cache에 상주 가능)
```

이분탐색 후 수십 개 메시지만 순차 스캔하면 되고, 이것도 page cache에 있으니 사실상 메모리 접근이다.
인덱스를 정밀하게 만드는 것보다, 작게 유지해서 캐시 적중률을 높이는 게 전체 성능에서 유리하다.

#### 세 가지 조합

```
프로듀서 append → OS가 page cache에 올림
                                  ↓
컨슈머 요청 → sparse index(page cache)에서 이분탐색으로 위치 찾기
                                  ↓
            .log 파일 해당 위치(page cache)를 zero-copy로 네트워크 전송
                                  ↓
         디스크 I/O가 발생하는 시점이 거의 없다
```

> 💡 **더 파보면 좋을 것**: `mmap`과 `sendfile`의 차이, 리눅스의 `vm.dirty_ratio`와 `vm.dirty_background_ratio`가 Kafka 성능에 미치는 영향, JVM 힙 사이즈를 작게 잡고 OS에 메모리를 많이 양보하는 Kafka 튜닝 원칙.

---

## 5. Spring Boot에서의 Kafka 설정

### 5-1. 의존성

```gradle
implementation 'org.springframework.kafka:spring-kafka'
```

### 5-2. Producer 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all                    # ISR 전체 복제 확인
      retries: 3                   # 실패 시 재시도 횟수
      properties:
        enable.idempotence: true   # 재시도 시 중복 발행 방지
        max.in.flight.requests.per.connection: 5  # 멱등성과 함께 순서 보장
        linger.ms: 5               # 5ms 기다렸다가 배치 전송
        batch.size: 16384          # 배치 크기 (16KB)
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `acks` | 1 | 0: 확인 안 함, 1: leader만, all: ISR 전체 |
| `retries` | 2147483647 | 전송 실패 시 재시도 횟수 |
| `enable.idempotence` | true | 재시도 중복 방지 (PID + sequence number) |
| `linger.ms` | 0 | 배치를 채우기 위해 기다리는 시간 |
| `batch.size` | 16384 | 배치 최대 크기 (bytes) |
| `max.in.flight.requests.per.connection` | 5 | 응답 안 받고 보낼 수 있는 최대 요청 수 |
| `compression.type` | none | 압축 (none, gzip, snappy, lz4, zstd) |

### 5-3. Consumer 설정

```yaml
spring:
  kafka:
    consumer:
      group-id: order-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest  # 그룹에 커밋된 offset이 없을 때 처음부터
      enable-auto-commit: false    # 수동 커밋 사용
      properties:
        max.poll.records: 500      # poll()당 최대 메시지 수
        max.poll.interval.ms: 300000  # poll() 간 최대 간격 (5분)
        session.timeout.ms: 45000    # 하트비트 타임아웃
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `group.id` | - | 컨슈머 그룹 ID |
| `auto.offset.reset` | latest | earliest: 처음부터, latest: 최신부터 |
| `enable.auto.commit` | true | false로 설정하면 수동 커밋 |
| `auto.commit.interval.ms` | 5000 | 자동 커밋 간격 |
| `max.poll.records` | 500 | 한 번 poll()에 가져오는 최대 레코드 수 |
| `max.poll.interval.ms` | 300000 | poll() 호출 간 최대 허용 시간 (초과 시 리밸런스) |
| `session.timeout.ms` | 45000 | 하트비트 타임아웃 (초과 시 그룹에서 제거) |

### 5-4. @KafkaListener 옵션

```java
@KafkaListener(
    topics = "order-events",           // 구독할 토픽
    groupId = "payment-service",       // 컨슈머 그룹 ID
    concurrency = "3",                 // 내부 컨슈머 스레드 수
    containerFactory = "kafkaListenerContainerFactory"  // 커스텀 팩토리
)
public void onMessage(
    @Payload OrderEvent event,         // 메시지 본문
    @Header(KafkaHeaders.OFFSET) long offset,           // 현재 offset
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, // 파티션 번호
    Acknowledgment ack                 // 수동 커밋용
) {
    processOrder(event);
    ack.acknowledge();  // 수동 커밋
}
```

### 5-5. 한 서비스가 Producer이면서 Consumer인 경우

커머스에서 흔한 패턴이다.

```java
@Service
public class OrderService {
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 프로듀서: 주문 생성 후 이벤트 발행
    public void createOrder(OrderRequest request) {
        Order order = orderRepository.save(toEntity(request));
        kafkaTemplate.send("order-events",
            order.getId().toString(),
            toJson(new OrderCreatedEvent(order)));
    }

    // 컨슈머: 결제 완료 이벤트 수신
    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.getOrderId());
        order.confirmPayment();
        orderRepository.save(order);
    }
}
```

> 📎 공식 문서: https://docs.spring.io/spring-kafka/reference/

> 💡 **더 파보면 좋을 것**: `ErrorHandler`와 `RetryTemplate` 커스터마이징, `@RetryableTopic`을 사용한 재시도/DLQ 자동화, `ConcurrentKafkaListenerContainerFactory` 커스텀 설정으로 에러 핸들링과 스레드 모델 제어.

---

## 6. 운영 전략 — "Kafka를 잘 쓰고 있는가" 체크리스트

### 6-1. 오프셋 커밋 전략

오프셋 커밋은 "이 컨슈머 그룹이 여기까지 읽었다"를 Kafka에 기록하는 행위이다.
커밋 전략이 올바르지 않으면 **메시지 유실 또는 중복 처리**가 발생한다.

#### Auto Commit (기본값)

```java
// enable.auto.commit=true (기본)
// auto.commit.interval.ms=5000 (기본 5초)
@KafkaListener(topics = "order-events")
public void onMessage(OrderEvent event) {
    processOrder(event);
    // poll() 시 이전 batch의 offset이 자동 커밋
}
```

- **문제 1 (유실)**: 메시지를 받았지만 처리 완료 전에 자동 커밋 → 컨슈머 죽으면 처리 안 된 메시지 유실
- **문제 2 (중복)**: 처리는 끝났는데 커밋 전에 죽으면 → 재시작 시 같은 메시지 다시 처리
- **적합한 곳**: 로그, 메트릭 등 소량 유실/중복 허용 데이터

#### 수동 동기 커밋 (권장)

```java
// enable.auto.commit=false
@KafkaListener(topics = "order-events")
public void onMessage(OrderEvent event, Acknowledgment ack) {
    processOrder(event);    // 비즈니스 로직 완료
    saveToDb(event);        // DB 저장 완료
    ack.acknowledge();      // 확실히 끝난 후 커밋
}
```

- 처리 완료 후에만 커밋 → 유실 방지
- `ack.acknowledge()` 직전에 죽으면 재시작 시 같은 메시지를 다시 받음 → **컨슈머 멱등성 필수**
- **적합한 곳**: 결제, 주문, 포인트 등 유실 불가 데이터

### 6-2. 파티션 키 설계

파티션 키는 **"어떤 메시지끼리 같은 파티션에 들어가야 하는가"**를 결정한다.
같은 파티션 = 순서 보장이므로, 비즈니스적으로 순서가 중요한 단위를 키로 잡는다.

| 상황 | 파티션 키 | 이유 |
|------|-----------|------|
| 주문 이벤트 | `orderId` | 같은 주문의 생성→결제→배송 순서 보장 |
| 유저 행동 로그 | `userId` | 같은 유저의 행동 순서 보장 |
| 재고 변경 | `itemId` | 같은 상품의 차감/복원 순서 보장 |
| 전체 순서 불필요한 경우 | `null` | 라운드로빈 → 파티션 간 균등 분배 |

```java
// 좋은 예: orderId로 키 설정
kafkaTemplate.send("order-events", order.getId().toString(), payload);
// → 같은 주문의 이벤트는 항상 같은 파티션, 순서 보장

// 나쁜 예: 키 없이 전송
kafkaTemplate.send("order-events", payload);
// → 같은 주문의 이벤트가 다른 파티션으로 갈 수 있음, 순서 깨짐
```

**주의: 파티션 키 편향 (hot partition)**

특정 키에 트래픽이 집중되면 한 파티션에 부하가 몰린다.
예: 대형 셀러의 `sellerId`를 키로 쓰면, 해당 셀러의 주문이 몰리는 파티션이 생긴다.
이 경우 키를 `sellerId + orderId` 조합으로 세분화하거나, 커스텀 파티셔너를 구현한다.

> 💡 **고민해볼 것**: 파티션 수를 늘리면 같은 키의 메시지가 다른 파티션으로 갈 수 있다(`hash(key) % 파티션 수`가 바뀌므로). 운영 중 파티션 증설 시 기존 키 라우팅이 깨지는 문제를 어떻게 해결할 것인가?

### 6-3. 재처리 시나리오

#### offset 되감기 방식들

```bash
# 특정 시각으로 되감기 (가장 자주 사용)
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-service \
  --topic order-events \
  --reset-offsets \
  --to-datetime 2026-03-21T15:00:00.000 \
  --execute

# 특정 offset으로 되감기
--to-offset 500

# 맨 처음부터 다시
--to-earliest

# 현재 시점부터 (과거 무시)
--to-latest

# 상대적 되감기 (100개 뒤로)
--shift-by -100
```

#### 실무 복구 절차

```
1. 문제 인지
   → 모니터링에서 에러율 급등 감지
   → 원인 분석: 15:00 배포 이후 발생

2. 컨슈머 중지
   → 잘못된 처리가 더 이상 진행되지 않도록

3. 버그 수정 및 배포

4. 오프셋 되감기
   → --to-datetime 2026-03-21T15:00:00.000

5. 컨슈머 재시작
   → 15:00부터 모든 메시지를 재처리
   → 이미 정상 처리된 메시지도 다시 들어옴
   → 컨슈머가 멱등해야 안전
```

#### 멱등성 보장 패턴

```java
@KafkaListener(topics = "order-events")
public void onMessage(OrderEvent event, Acknowledgment ack) {
    // 이미 처리한 이벤트인지 확인
    if (processedEventRepository.existsById(event.getEventId())) {
        ack.acknowledge();  // 스킵하고 커밋
        return;
    }

    processOrder(event);
    processedEventRepository.save(event.getEventId());
    ack.acknowledge();
}
```

이벤트 ID 기반 중복 체크로, 되감기 시 이미 처리한 메시지를 안전하게 스킵한다.

### 6-4. 장애 복구 전략 문서화

"장애가 나면 어떻게 할 건가?"에 대한 답이 문서로 존재해야 한다.

#### 문서화해야 할 항목

**브로커 장애 시:**
- ISR 동작 방식과 leader 전환 과정
- `min.insync.replicas` 설정값
- 모니터링 알림 경로

**컨슈머 lag 폭발 시:**
- lag 임계치와 대응 절차
- 컨슈머 스케일아웃 절차
- 파티션 수 증설 절차

**메시지 유실 의심 시:**
- 프로듀서 로그, 컨슈머 offset 확인 방법
- 되감기 절차 및 전제 조건 (멱등성)

**Poison Pill (파싱 불가 메시지) 처리:**

```java
@KafkaListener(topics = "order-events")
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),
    dltTopicSuffix = ".DLQ"
)
public void onMessage(OrderEvent event) {
    processOrder(event);
    // 3번 실패하면 order-events.DLQ 토픽으로 자동 이동
}
```

**운영 명령어 치트시트:**

```bash
# 컨슈머 그룹 lag 확인
kafka-consumer-groups.sh --describe --group payment-service \
  --bootstrap-server localhost:9092

# 토픽 파티션 상태 확인 (ISR, leader 등)
kafka-topics.sh --describe --topic order-events \
  --bootstrap-server localhost:9092

# 특정 시점으로 되감기
kafka-consumer-groups.sh --reset-offsets \
  --to-datetime 2026-03-21T15:00:00.000 \
  --group payment-service --topic order-events --execute

# 컨슈머 그룹 삭제 (offset 초기화)
kafka-consumer-groups.sh --delete --group payment-service
```

### 6-5. Transactional Outbox 패턴 (프로듀서 안전장치)

프로듀서가 `kafkaTemplate.send()` 호출 직후, 재시도하기 전에 죽을 수 있다.
이 경우 메시지는 Kafka에도 없고, 재시도할 주체도 없다.

**Outbox 패턴**은 비즈니스 데이터와 발행할 이벤트를 **같은 DB 트랜잭션**으로 저장하고,
별도 스케줄러가 outbox 테이블을 폴링해서 Kafka로 발행한다.

```java
// 프로듀서 서비스
@Transactional
public void completePayment(PaymentResult result) {
    payment.complete(result);
    paymentRepository.save(payment);                    // 비즈니스 데이터

    outboxRepository.save(new OutboxEvent(              // 같은 트랜잭션
        "payment-events",
        payment.getId().toString(),
        toJson(new PaymentCompletedEvent(payment))
    ));
}

// 별도 스케줄러
@Scheduled(fixedDelay = 1000)
public void publishOutbox() {
    List<OutboxEvent> pending = outboxRepository.findUnpublished();
    for (OutboxEvent event : pending) {
        kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload());
        event.markPublished();
    }
}
```

핵심: 트랜잭션 안에서 하는 일이 **같은 DB에 INSERT 2번**이다. 외부 호출 없이 수 밀리초에 끝난다.
이전의 긴 동기 트랜잭션(재고+PG+포인트+알림)과는 질적으로 다르다.

### 6-6. 프로듀서-컨슈머 안전장치 정리

Kafka를 안전하게 쓰려면 **양쪽 다** 장치가 필요하다:

| 위치 | 패턴 | 목적 | 필요한 테이블 |
|------|------|------|--------------|
| 프로듀서 | Transactional Outbox | 발행 누락 방지 | outbox 테이블 |
| 컨슈머 | 멱등성 체크 | 중복 처리 방지 | processed_event 테이블 |

각 서비스의 역할에 따라 테이블이 배치된다:

```
주문 서비스 DB (프로듀서)
├── order 테이블
└── outbox 테이블          ← 발행 누락 방지

결제 서비스 DB (컨슈머 + 프로듀서)
├── payment 테이블
├── processed_event 테이블  ← 중복 처리 방지 (컨슈머)
└── outbox 테이블           ← 발행 누락 방지 (프로듀서)
```

> 💡 **더 파보면 좋을 것**: Debezium Outbox Event Router를 사용하면 스케줄러 없이 CDC로 outbox 테이블 변경을 감지해서 자동 발행할 수 있다. Polling 방식 대비 지연이 줄어든다.
> 고민해볼 것: "processed_event 테이블이 무한히 커지는 것을 어떻게 관리할 것인가?" retention 전략이 필요하다.

---

## 7. 주요 설정값 요약

### 7-1. 브로커 설정

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `log.segment.bytes` | 1GB | 세그먼트 최대 크기, 초과 시 롤오버 |
| `log.roll.ms` | 7일 | 세그먼트 최대 유지 시간 |
| `log.retention.hours` | 168 (7일) | 메시지 보존 기간 |
| `log.retention.bytes` | -1 (무제한) | 파티션당 최대 보존 크기 |
| `log.index.interval.bytes` | 4096 | sparse index 기록 간격 |
| `segment.index.bytes` | 10MB | 인덱스 파일 최대 크기 |
| `min.insync.replicas` | 1 | 쓰기 허용 최소 ISR 수 |
| `default.replication.factor` | 1 | 토픽 기본 복제 팩터 |
| `num.partitions` | 1 | 토픽 기본 파티션 수 |

### 7-2. 운영 환경 권장 설정 조합

```properties
# 데이터 안전성 (결제/주문)
acks=all
min.insync.replicas=2
replication-factor=3
enable.idempotence=true

# 컨슈머 안정성
enable.auto.commit=false
auto.offset.reset=earliest
max.poll.interval.ms=300000

# 성능 튜닝
linger.ms=5
batch.size=32768
compression.type=lz4
```

> 📎 전체 설정 목록: https://kafka.apache.org/documentation/#configuration

---

## 8. 부록: 자주 쓰는 운영 명령어

```bash
# 토픽 목록 조회
kafka-topics.sh --list --bootstrap-server localhost:9092

# 토픽 생성
kafka-topics.sh --create --topic order-events \
  --partitions 6 --replication-factor 3 \
  --bootstrap-server localhost:9092

# 토픽 상세 정보 (파티션, ISR, leader 확인)
kafka-topics.sh --describe --topic order-events \
  --bootstrap-server localhost:9092

# 컨슈머 그룹 목록
kafka-consumer-groups.sh --list --bootstrap-server localhost:9092

# 컨슈머 그룹 lag 확인
kafka-consumer-groups.sh --describe --group payment-service \
  --bootstrap-server localhost:9092

# 메시지 직접 조회 (디버깅)
kafka-console-consumer.sh --topic order-events \
  --from-beginning --max-messages 10 \
  --bootstrap-server localhost:9092

# 특정 시점으로 offset 되감기
kafka-consumer-groups.sh --reset-offsets \
  --to-datetime 2026-03-21T15:00:00.000 \
  --group payment-service --topic order-events \
  --bootstrap-server localhost:9092 --execute

# offset을 처음으로 되감기
kafka-consumer-groups.sh --reset-offsets \
  --to-earliest --group payment-service --topic order-events \
  --bootstrap-server localhost:9092 --execute

# .log 파일 내용 덤프 (세그먼트 디버깅)
kafka-run-class.sh kafka.tools.DumpLogSegments \
  --deep-iteration --print-data-log \
  --files /var/kafka-logs/order-events-0/00000000000000000000.log
```

---

## 참고 자료

- Apache Kafka 공식 문서: https://kafka.apache.org/documentation/
- Kafka GitHub 소스코드: https://github.com/apache/kafka
- Spring Kafka 공식 문서: https://docs.spring.io/spring-kafka/reference/
- Spring Kafka GitHub: https://github.com/spring-projects/spring-kafka
- Kafka 설정 전체 목록: https://kafka.apache.org/documentation/#configuration
- Broker 설정: https://kafka.apache.org/documentation/#brokerconfigs
- Producer 설정: https://kafka.apache.org/documentation/#producerconfigs
- Consumer 설정: https://kafka.apache.org/documentation/#consumerconfigs
- Topic 설정: https://kafka.apache.org/documentation/#topicconfigs
- Connect 문서: https://kafka.apache.org/documentation/#connect

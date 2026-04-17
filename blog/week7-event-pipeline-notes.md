7주차 이벤트 파이프라인 — 테크니컬 라이팅 소재 노트

> 이 파일은 블로그 글의 "소재 창고"다. 설계 과정에서의 고민, 트레이드오프 분석, 결정과 근거를 그때그때 기록한다.

---

## 소재 1: 핵심 vs 부가 로직 판단 — "이것이 실패하면 사용자 요청이 실패해야 하는가?"

**고민**: 좋아요의 incrementLikeCount는 핵심인가 부가인가?

좋아요를 누른 사용자에게 "좋아요 등록 완료"라고 응답했는데, 집계가 실패해서 목록의 좋아요 수가 반영 안 된다면? 사용자 입장에서는 좋아요를 눌렀는데 숫자가 안 올라간 것처럼 보인다.
``
처음엔 "집계 실패해도 좋아요 자체는 성공"이라고 판단했다. 하지만 즉시 반영 UX를 위해 incrementLikeCount를 AFTER_COMMIT에서 best-effort로 실행하기로 했다.

**결정**: 핵심은 아니지만 UX를 위해 best-effort 즉시 반영 + Kafka 집계 + 배치 대사로 3중 안전망 구축.

**트레이드오프**: 즉시 반영(UX) vs 트랜잭션 분리(안정성). 둘 다 잡되, 실패 시 최종 정합성은 Kafka+배치가 보장.

**라이팅 포인트**: "핵심/부가 판단은 기술적 판단이 아니라 비즈니스 판단이다. 같은 연산이라도 UX 관점에서는 다른 답이 나올 수 있다."

---

## 소재 2: Outbox Poller 중복 처리 — 놓치는 것 vs 중복 처리

**고민**: 다중 인스턴스에서 Outbox Poller가 같은 행을 두 번 처리하면?

첫 반응: SELECT FOR UPDATE SKIP LOCKED로 행 잠금 → 중복 방지.

**반론 (사용자 피드백)**: DB 커넥션을 잠금으로 점유하는 게 대량 트래픽에서 더 치명적이지 않나? 놓치는 것보다 중복 처리가 낫지 않나?

**분석**:
- SKIP LOCKED: Kafka 장애 시 잠긴 행을 아무도 재시도 못함. 커넥션 점유 → API 응답 지연
- 중복 허용: Consumer의 event_handled PK lookup으로 중복 걸러냄. 비용 = PK lookup 1회 (~0.1ms)

놓치는 것 >> 중복 처리. Consumer가 멱등하면 중복은 무해하다.

**결정**: 잠금 없는 단순 SELECT + Consumer 멱등 처리. At Least Once.

**라이팅 포인트**: "동시성 제어의 관점을 바꾸자. Producer 쪽에서 중복을 막으려고 DB 잠금을 쓰면, 정작 막아야 할 것(이벤트 유실)이 발생한다. 중복 제어는 Consumer에게 맡기자."

---

## 소재 3: Debezium vs Poller — 망치로 호두 까기?

**고민**: Outbox → Kafka 발행에 CDC(Debezium)를 쓸 것인가, 단순 Poller를 쓸 것인가?

**Poller**: @Scheduled 1개면 끝. 5초 주기. 인프라 추가 없음.
**Debezium**: Kafka Connect 클러스터 + MySQL binlog 설정 + Connector 관리.

처음 분석: "event_outbox 하나를 5초마다 폴링하는 데 Kafka Connect 클러스터를 추가하는 건 망치로 호두 까기"

**전환 이유**: 실무 적용 전 설정 경험 확보에 의의. 학습 프로젝트에서 과도한 인프라를 일부러 경험하는 것은 가치 있다.

**Debezium을 선택함으로써 달라진 점**:
1. Outbox 테이블에 status 컬럼 불필요 (binlog에서 읽으므로)
2. 테이블 정리가 극적으로 단순해짐 (1시간 보존 후 DELETE)
3. Near real-time 발행 (5초 → 수백 ms)
4. 다중 인스턴스 중복 발행 문제 원천 해결

**트레이드오프**: 인프라 복잡도 ↑↑, 운영 난이도 ↑ / 안정성 ↑, 지연 ↓, 중복 해결

**라이팅 포인트**: "올바른 선택은 컨텍스트에 따라 다르다. 프로덕션이라면 Poller로 시작하고 규모가 커지면 Debezium으로 전환하는 것이 맞다. 학습이라면 일부러 어려운 길을 가는 것이 맞다."

---

## 소재 4: 모놀리스에서 Kafka를 쓰는 의미

**고민**: "MSA가 아닌데 Kafka를 왜 쓰지?"

이 프로젝트는 모놀리스가 아니다. commerce-api, commerce-streamer, commerce-batch — 3개의 독립 JVM 프로세스. 하지만 같은 DB를 공유한다.

**핵심**: Kafka는 MSA 전용 기술이 아니라, 프로세스 간 비동기 통신 인프라.

MSA에서 Kafka가 필요한 이유: 서비스 간 데이터 동기화 (각자 DB)
멀티 프로세스에서 Kafka가 필요한 이유: 프로세스 간 메시지 전달 + 부하 분리

현재 아키텍처에서 Kafka 없이 가능한 대안:
- ApplicationEvent: 같은 JVM 안에서만 동작 → streamer가 받을 수 없음
- DB 폴링: Kafka가 하는 것과 동일하지만 더 느리고 비효율적
- Redis Pub/Sub: 구독자 없으면 메시지 유실
- HTTP 호출: 동기 + 장애 전파

**결정**: Kafka는 멀티 프로세스 아키텍처의 필연적 선택.

**라이팅 포인트**: "Kafka를 '마이크로서비스 아키텍처의 도구'로 한정하면 가능성의 절반을 잃는다. 프로세스 간 비동기 통신이 필요한 모든 곳에서 Kafka는 유효하다."

---

## 소재 5: Outbox 테이블 정리 — 라운드 로빈 vs 파티셔닝 vs Debezium + DELETE

**고민**: 쿠팡급 일 150만 건, 연 5.5억 건이 쌓이는 Outbox를 어떻게 정리하나?

분석한 방법: Batch DELETE, 라운드 로빈, PARTITION DROP, MySQL EVENT, Debezium + 단순 DELETE

**라운드 로빈의 치명적 문제**: JPA Entity는 테이블명이 고정(@Table(name="...")). 두 테이블 교대 → Native Query 강제 → DIP 위반. 코드가 인프라 구현에 오염된다.

**PARTITION BY RANGE**: JPA 호환, DROP PARTITION O(1). 하지만 PK에 파티션키(created_at) 포함 필수 → 복합 PK 강제.

**반전**: Debezium을 도입하면 테이블이 "큐"가 아니라 "쓰기 로그"로 변한다. 1시간 보존이면 최대 6.25만 건. 이 규모에서 DELETE는 ~1초.

**결정**: Debezium + 단순 Batch DELETE. "복잡한 문제가 아니라, 문제를 복잡하게 만들지 않는 것."

**라이팅 포인트**: "상위 설계 결정(Debezium 도입)이 하위 문제(테이블 정리)를 소멸시킨 사례. 개별 문제를 최적화하기 전에, 문제 자체를 없앨 수 있는 상위 결정이 있는지 먼저 살펴보자."

---

## 소재 6: @Async 스레드 풀 — "DB 커넥션과 싸우지 마라"

**고민**: @Async 스레드 풀 크기를 어떻게 잡나?

첫 분석: 피크 TPS 기반으로 core=4, max=8 추천.
재분석: @Async에서 실행되는 작업이 DB 커넥션을 쓰는가?

- incrementLikeCount → 동기 (Tomcat 스레드에서 실행) → DB 커넥션은 Tomcat 풀 몫
- 캐시 무효화 → 동기 → Redis (Lettuce NIO, 풀 불필요)
- 유저 로깅 → @Async → DB/Redis 불필요
- Kafka 발행 → @Async → KafkaTemplate.send()는 논블로킹

**결정**: @Async 작업은 모두 초경량. core=2, max=4로 충분. "DB 커넥션 풀과 경합하지 않는 것을 확인한 후에야 풀 크기를 결정할 수 있다."

**라이팅 포인트**: "스레드 풀 크기는 작업 수가 아니라, 작업이 잡는 자원으로 결정한다. CPU 바운드 작업에 큰 풀은 컨텍스트 스위칭 비용만 늘린다."

---

## 소재 7: Kafka 설정 점검 — value-serializer 오타가 동작하는 이유

**발견**: kafka.yml의 consumer 섹션에 `value-serializer` (serializer 키에 Deserializer 클래스).

Spring Boot는 이 키를 무시한다 (consumer에는 `value-deserializer` 키만 인식). 그런데 동작하는 이유: KafkaConfig.java에서 ByteArrayJsonMessageConverter를 설정해서 변환을 대체하고 있다.

**교훈**: "설정이 잘못되어도 다른 계층이 보완해서 동작하면, 문제를 발견하기 어렵다. 코드 리뷰에서 설정 파일도 검증 대상이다."

---

## 소재 8: Kafka Config 심층 분석 — "설정은 개별이 아니라 조합으로 검증해야 한다"

설계 명세를 작성하고 Kafka 기술 가이드 키워드(acks, min.insync.replicas, idempotency, zero-copy, page cache, KRaft)로 리뷰하면서 7가지 문제를 발견했다. 핵심은 **개별 설정값이 아니라 설정 간 상호작용**을 이해하지 못하면 "설정했는데 안 되는" 상황이 발생한다는 것.

### 8-1. acks=all이 무의미해지는 순간

`acks=all`은 "ISR(In-Sync Replicas) 전원에게 기록 확인"이다. 그런데 **브로커가 1대뿐이면 ISR = {Leader 1대}**이므로 `acks=all ≡ acks=1`이다. `acks=all`이 의미를 갖으려면:

| 조합 | 효과 |
|---|---|
| acks=all + replicas=1 | acks=1과 동일 — 무의미 |
| acks=all + replicas=3 + min.insync.replicas=1 | Leader만 확인 — 여전히 약함 |
| acks=all + replicas=3 + min.insync.replicas=2 | Leader + 최소 1 Follower 확인 — **프로덕션 권장** |

**라이팅 포인트**: "acks=all은 '완벽한 안전'이 아니라, min.insync.replicas와 조합될 때만 의미가 있다. 설정의 의미는 단독이 아니라 조합에서 나온다."

### 8-2. enable.idempotence=true + retries=3의 모순

Idempotent Producer는 내부적으로 `retries=Integer.MAX_VALUE`를 강제한다. 그런데 `retries: 3`을 yml에 명시하면 **기본값을 덮어쓴다**. 결과: 3회 재시도 후 포기 → 메시지 유실 가능. idempotent producer의 핵심 보장("절대 유실하지 않음")이 깨진다.

올바른 제어: `retries`를 건드리지 않고, `delivery.timeout.ms`(기본 120초)로 **시간 기반** 제어.

**고민**: 왜 이 실수가 흔한가? — Spring Boot의 yml 설정이 Kafka 클라이언트의 기본값 체계를 **완전히 무시**하기 때문이다. `retries: 3`은 "3번만 재시도하세요"라는 명시적 지시이고, enable.idempotence의 암묵적 기본값(MAX_VALUE)보다 우선한다. **명시 > 암묵**이라는 설정 우선순위 원칙이 여기서 함정이 된다.

**라이팅 포인트**: "프레임워크가 자동으로 설정해주는 값을 '직접 설정'으로 덮어쓰는 순간, 자동 설정의 의도도 함께 덮어쓴다. 설정을 추가하기 전에, '이 값을 내가 관리해야 하는가?'를 먼저 질문하자."

### 8-3. Consumer 멱등성의 원자성 갭

초기 설계: `비즈니스 로직 실행 → event_handled INSERT`. 이 두 단계 사이에 크래시가 발생하면?

```
비즈니스 로직 성공 (product_metrics +1)
                ← 여기서 크래시
event_handled INSERT (실행 안 됨)
→ 재시작 시 event_handled에 없음 → 다시 처리 → product_metrics +1 (중복)
```

**해결**: INSERT-first 패턴 — `event_handled INSERT → 비즈니스 로직`을 **단일 트랜잭션**으로 묶는다.

- event_handled INSERT 성공 → 비즈니스 로직 실행 → TX 커밋: 정상 흐름
- 비즈니스 로직 실패 → TX 롤백: event_handled도 롤백 → 재시도 가능
- TX 커밋 후 크래시 → 재시작 시 event_handled에 이미 존재 → skip

**라이팅 포인트**: "멱등 처리에서 '확인'과 '실행'이 원자적이지 않으면, 멱등이 아니다. 체크와 실행 사이의 갭이 바로 장애가 파고드는 틈이다."

### 8-4. 놓치기 쉬운 설정들

| 설정 | 역할 | 왜 놓치나 |
|---|---|---|
| `isolation.level: read_committed` | TX 커밋된 메시지만 읽기 | Debezium이 TX 단위로 발행하므로 필수인데, Consumer 설정이라 Producer 설계 시 빠짐 |
| `auto.offset.reset: earliest` | 신규 Consumer Group이 처음부터 읽기 | 기본값 `latest` → 기존 메시지 유실 |
| `max.poll.interval.ms` | poll() 간격 초과 시 리밸런싱 | SINGLE_LISTENER에서 건별 CAS UPDATE → 기본 5분 초과 가능 |
| `compression.type: lz4` | 배치 압축 | "압축은 나중에"라는 생각 → 초기부터 설정해야 Broker 디스크 + 네트워크 절약 |

### 8-5. Zero-Copy와 OS Page Cache — 배치/압축 설정의 물리적 근거

Kafka가 빠른 이유를 두 가지 OS 최적화로 설명할 수 있다:

1. **Zero-Copy**: Broker → Consumer 전송 시 `sendfile()` 시스템콜 사용. 디스크 → 커널 버퍼 → 네트워크 소켓으로 **유저 스페이스를 거치지 않고** 직접 전달. CPU 사용량과 메모리 복사 최소화.

2. **OS Page Cache**: Broker는 메시지를 JVM 힙이 아닌 OS 페이지 캐시에 저장. 최근 메시지는 디스크 I/O 없이 메모리에서 서빙.

이 두 최적화의 효율을 극대화하는 것이 `linger.ms`, `batch.size`, `compression.type` 설정의 **물리적 근거**다:
- 작은 메시지를 하나씩 보내면 → 네트워크 라운드트립 N배 + sendfile 호출 N배
- 배치로 묶어서 보내면 → 한 번의 sendfile로 큰 블록 전송 + 압축으로 페이지 캐시 적중률 향상

**라이팅 포인트**: "설정값의 의미를 물리 계층까지 추적하면, '왜 이 값인가'에 답할 수 있다. linger.ms=50은 '50ms 지연'이 아니라, 'Zero-Copy 한 번의 전송량을 극대화하는 버퍼링 시간'이다."

### 8-6. Consumer Group 분리 — 처리 특성이 다르면 격리하라

MetricsConsumer(배치 UPSERT)와 CouponIssueConsumer(건별 CAS)가 같은 group-id를 공유하면: 쿠폰 발급의 건별 처리 지연 → group 전체 리밸런싱 → 메트릭 집계까지 중단.

**결정**: `metrics-collector`, `coupon-issuer`로 분리. 처리 특성(배치 vs 건별), 부하 패턴(상시 vs 이벤트성), 장애 영향 범위를 기준으로 Consumer Group을 설계한다.

---

## (기록 예정)

- [x] 08 설계 명세 작성 시 최종 설계 결정 기록
- [ ] Phase별 구현 시 구현 고민점/문제점/해결 흐름 추가
- [ ] Debezium 셋업 과정에서의 삽질 기록
- [ ] 선착순 쿠폰 동시성 테스트 결과와 인사이트

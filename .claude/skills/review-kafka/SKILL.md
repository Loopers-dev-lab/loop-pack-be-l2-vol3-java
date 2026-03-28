---
name: review-kafka
description:
  프로젝트 전체에서 Kafka 관련 코드(Producer, Consumer, Config, Outbox, DLQ 등)를 자동 탐색하여 분석한다.
  Producer/Consumer 설정, Transactional Outbox 패턴, 멱등성, Partition Key 설계, DLQ 구성,
  이벤트 설계, 선착순 쿠폰 발급 구조, 오프셋 커밋 전략을 중심으로 리스크를 드러낸다.
  단순한 정답 제시가 아니라, 현재 구조의 의도와 trade-off를 드러내고 개선 가능 지점을 선택적으로 판단할 수 있도록 돕는다.
---

Kafka 관련 코드를 리뷰할 때 반드시 다음 흐름을 따른다.

### 분석 준비

프로젝트 전체에서 Kafka 관련 코드를 자동 탐색한다.
- KafkaConfig, ProducerConfig, ConsumerConfig 등 설정 클래스
- KafkaTemplate, Producer, Consumer, @KafkaListener 사용 코드
- Outbox 엔티티, 스케줄러/릴레이
- DLQ 관련 코드
- 선착순 쿠폰 발급 관련 코드 (Redis INCR gate 등)

> 특정 파일만 떼어내어 판단하지 않는다. Producer → Outbox → Consumer → DLQ 전체 파이프라인을 기준으로 분석한다.

---

### 1. Producer 설정 점검

다음을 순서대로 확인한다.
- `acks=all` 설정 여부
  - `acks=1`이면 리더 장애 시 메시지 유실 가능성
- `enable.idempotence=true` 설정 여부
  - 미설정 시 Producer 재시도로 인한 메시지 중복 가능성
- `max.in.flight.requests.per.connection` <= 5 여부
  - 멱등성 활성화 시 5 초과하면 순서 보장 불가
- `retries`, `retry.backoff.ms` 설정 적절성
  - 재시도 횟수가 과도하거나, backoff 없이 즉시 재시도하는지
- kafkaTemplate.send() 직접 호출 vs Outbox 패턴 사용
  - 트랜잭션 내부에서 직접 send() 호출 시 DB 커밋과 Kafka 발행의 원자성 깨짐

---

### 2. Transactional Outbox 패턴 검증

다음을 중심으로 분석한다.
- 비즈니스 데이터와 Outbox 이벤트가 **같은 DB 트랜잭션**에 저장되는지
  - 별도 트랜잭션이면 비즈니스 커밋 성공 + Outbox 저장 실패 가능
- Outbox 엔티티 필수 필드 점검
  - eventId(UNIQUE): 중복 발행 방지
  - published: 발행 여부 추적
  - topic: 대상 토픽
  - partitionKey: 순서 보장 단위
  - payload: 이벤트 데이터
- 별도 스케줄러/릴레이가 Outbox -> Kafka 발행을 담당하는지
  - Application 코드에서 직접 발행하면 Outbox 패턴의 의미 퇴색
- 발행 실패 시 재시도 로직
  - 실패한 레코드가 영구적으로 미발행 상태로 남는지
  - 개별 실패 vs 전체 배치 실패 처리 구분
- 트랜잭션 내부에서 kafkaTemplate.send() 직접 호출하는 곳은 없는지
  - DB 롤백 시 이미 발행된 메시지는 회수 불가

---

### 3. Consumer 설정 점검

다음을 확인한다.
- `enable.auto.commit=false` + `AckMode.MANUAL` 설정 여부
  - auto commit이면 처리 실패 시에도 오프셋이 커밋되어 메시지 유실
- `auto.offset.reset` 설정 의도
  - `latest`: 신규 Consumer 합류 시 기존 메시지 건너뜀 — 의도적인지?
  - `earliest`: 전체 재처리 — 멱등성이 보장되는지?
- `max.poll.records` 적절성
  - 과도하게 크면 처리 시간 초과 -> 리밸런싱
- `max.poll.interval.ms`, `session.timeout.ms` 적절성
  - max.poll.interval.ms < 실제 배치 처리 시간이면 불필요한 리밸런싱
- heartbeat interval <= session timeout / 3 여부
  - 미준수 시 불필요한 Consumer 탈퇴 발생

---

### 4. Consumer 멱등성 (Idempotent Consumer)

다음을 순서대로 확인한다.
- 중복 처리 방지 메커니즘 존재 여부
  - `event_handled` 테이블 또는 유사한 중복 체크 테이블
  - eventId 존재 확인 -> skip 로직
- 멱등성 체크 + 비즈니스 로직이 **같은 트랜잭션**인지
  - 분리되어 있으면 체크 통과 후 비즈니스 로직 실패 시 재처리 불가
- `version`/`updatedAt` 기반 최신 이벤트만 반영하는지
  - 순서 역전된 이벤트가 최신 상태를 덮어쓸 가능성
- Optimistic Lock 충돌 시 재시도 전략
  - 단순 예외 전파 vs 재시도 vs DLQ 격리

---

### 5. Partition Key 설계

다음을 확인한다.
- 파티션 키가 명시적으로 설정되어 있는지
  - null key -> Round Robin -> 동일 Aggregate의 이벤트 순서 깨짐
- aggregateId 기반 키 설정 여부
  - 같은 Aggregate의 이벤트가 같은 파티션으로 가야 순서 보장
- Hot Partition 가능성
  - 특정 키(예: 인기 상품 ID)에 트래픽 집중 가능성
  - 파티션 수 대비 키 분포 적절성
- 토픽별 파티션 키 일관성
  - 동일 도메인 이벤트인데 토픽마다 다른 키를 사용하는지

---

### 6. DLQ (Dead Letter Queue) 구성

다음을 확인한다.
- Consumer 실패 시 DLQ 격리 로직 존재 여부
  - 무한 재시도 vs 일정 횟수 후 DLQ 전송
- DLQ 전송 실패 시 원본 메시지 ack 처리 방식
  - DLQ 전송 실패 + 원본 ack -> 메시지 유실
  - DLQ 전송 실패 + 원본 nack -> 무한 루프 가능성
- Poison Pill(파싱 불가 메시지) 처리
  - 역직렬화 실패 메시지가 Consumer를 영구적으로 블로킹하는지
  - ErrorHandlingDeserializer 또는 유사 메커니즘 존재 여부
- DLQ 후속 처리/모니터링 계획
  - DLQ에 쌓인 메시지를 누가, 어떻게 재처리하는지

---

### 7. 이벤트 설계 점검

다음을 확인한다.
- Command vs Event 구분 명확성
  - "쿠폰 발급해줘"(Command) vs "주문이 생성됨"(Event) 혼용 여부
  - Command는 실패 가능, Event는 이미 일어난 사실 — 처리 방식이 달라야 함
- 이벤트 envelope 구조
  - eventId, eventType, data가 분리되어 있는지
  - 또는 flat 구조로 메타와 데이터가 혼재되어 있는지
- 페이로드에 메타 정보 포함 여부
  - eventId: 멱등성 체크 키
  - eventType: Consumer 라우팅 기준
  - occurredAt: 순서 판단 기준
- 이벤트 스키마 버전 관리 고려 여부
  - 필드 추가/변경 시 기존 Consumer 호환성

---

### 8. 선착순 쿠폰 발급 점검

다음을 확인한다.
- API -> Kafka 발행만 하고 Consumer가 실제 발급하는 구조인지
  - API에서 직접 발급하면 동시성 제어 어려움
- 수량 제한 동시성 제어 메커니즘
  - Redis INCR gate 등 원자적 수량 체크
  - INCR 시점과 실제 발급 시점의 간극으로 인한 불일치 가능성
- 중복 발급 방지 (userId 기반)
  - 동일 사용자의 중복 요청 방어
  - DB UNIQUE 제약 vs 애플리케이션 레벨 체크
- 발급 결과 조회 구조
  - polling 기반 vs callback 기반
  - 사용자가 발급 결과를 언제, 어떻게 확인하는지
- 수량 초과 발급 방지 검증
  - Redis INCR 성공 후 실제 발급 실패 시 카운터 보정 여부
  - 카운터와 실제 발급 수의 정합성

---

### 9. 오프셋 커밋 전략 & 재처리 가능성

다음을 확인한다.
- 오프셋 커밋이 비즈니스 로직 완료 **이후** 수행되는지
  - 로직 시작 전 또는 중간에 커밋하면 실패 시 메시지 유실
- 배치 처리 시 개별 메시지 실패의 전체 배치 ack 영향
  - 1건 실패로 전체 배치 nack -> 성공한 메시지 재처리
  - 1건 실패 무시하고 전체 ack -> 실패 메시지 유실
- offset 되감기 시 멱등성 보장 여부
  - 운영 중 offset reset 시 Consumer가 안전하게 재처리 가능한지
- Consumer 재시작 시 처리 시작점 예측 가능성
  - 마지막 커밋된 offset부터 시작하는지
  - auto.offset.reset과의 조합 동작

---

### 톤 & 스타일 가이드
- 코드 레벨 수정안을 직접 제시하지 않는다.
- 설계를 비판하지 말고 리스크를 드러내는 리뷰 톤을 유지한다.
- Kafka는 항상 다음을 만족한다고 가정한다:
  - 메시지는 중복 전달될 수 있다
  - 순서는 파티션 내에서만 보장된다
  - Consumer는 언제든 리밸런싱될 수 있다
  - 네트워크 파티션이 발생할 수 있다
- 구현보다 파이프라인 전체의 신뢰성, 메시지 유실/중복, 순서 보장을 중심으로 분석한다.
- 설정값에 대해서는 "왜 이 값을 선택했는가?"를 질문하는 방식으로 접근한다.

# Kafka 운영 Runbook

이 문서는 프로젝트의 Kafka 파이프라인에 대한 오프셋 커밋 전략, 재처리 절차, 장애 복구 시나리오를 기술한다.
일반적인 Kafka 개념은 `kafka-reference.md`를 참조하고, 이 문서는 **이 프로젝트의 구체적 구현**에 한정한다.

---

## 1. 파이프라인 구성 요약

```
[commerce-api]                           [commerce-streamer]

 비즈니스 로직                             Consumer Group: loopers-default-consumer
   ↓ ApplicationEvent                       ├─ CatalogEventConsumer  (batch, concurrency=3)
 OutboxEventListener                        ├─ OrderEventConsumer    (batch, concurrency=3)
   ↓ @TransactionalEventListener            │
 outbox_events 테이블                      Consumer Group: commerce-streamer-coupon
   ↓ OutboxPublisher (1초 polling)          └─ CouponIssueConsumer   (single, concurrency=1)
 Kafka 토픽
   ├─ catalog-events
   ├─ order-events
   └─ coupon-issue-requests

 * ProductViewedEvent만 Outbox를 거치지 않고 Kafka에 직접 발행 (유실 허용)
```

---

## 2. 오프셋 커밋 전략

### 2-1. 공통 설정

| 항목 | 값 | 근거 |
|------|----|------|
| `enable.auto.commit` | `false` | 처리 완료 전 커밋 방지 (메시지 유실 차단) |
| `ack-mode` | `MANUAL` | 비즈니스 로직 완료 후 명시적 커밋 |
| `auto.offset.reset` | `latest` | Consumer Group 최초 등록 시에만 적용. 이미 등록된 그룹은 마지막 커밋 오프셋부터 재개 |

### 2-2. Batch Consumer (CatalogEvent, OrderEvent)

```
1. poll()로 최대 500건 수신
2. for 루프로 레코드 순회
   ├─ 성공: 비즈니스 로직 처리 (MetricsApplicationService 내부 TX)
   ├─ 실패: DLQ 동기 전송 (5초 타임아웃)
   └─ DLQ 전송 실패: RuntimeException throw → 외부 catch로 전파
3. for 루프 정상 완료 시 acknowledgment.acknowledge()
4. DLQ 전송 실패로 예외 발생 시 ack 미호출 → 전체 배치 재배달
```

**커밋 시점**: 배치 내 모든 레코드 처리(성공 or DLQ 전송) 완료 후 1회.
배치의 마지막 레코드 오프셋이 커밋된다. 중간에 DLQ로 보낸 레코드의 오프셋도 포함되므로, DLQ 전송 성공이 보장된 상태에서만 커밋된다.

**재배달 조건**: DLQ 전송이 실패한 경우에만 전체 배치가 재배달된다.
이미 성공 처리된 레코드는 `EventHandled` 테이블로 skip되므로 부수효과 없음. 단, DLQ에 이미 전송된 레코드가 다시 DLQ로 전송되어 중복 적재될 수 있다.

### 2-3. Single Consumer (CouponIssue)

```
1. poll()로 1건 수신
2. 처리 시도
   ├─ 성공: ack.acknowledge()
   └─ 실패: DLQ 동기 전송 → ack.acknowledge()
            DLQ 전송도 실패 시 → ack 미호출 → 재배달
```

**커밋 시점**: 레코드 1건 처리 후 즉시.
실패 시에도 DLQ 전송 성공 후 ack하여 무한 재배달 루프를 방지한다.

### 2-4. 오프셋 커밋 흐름도

```
정상 처리:
  poll → process → ack → commit   ← 다음 poll

개별 레코드 실패 (Batch):
  poll → [process, process, FAIL → DLQ, process] → ack → commit

DLQ 전송 실패 (Batch):
  poll → [process, FAIL → DLQ FAIL → throw] → ack 안 함 → 전체 재배달

개별 레코드 실패 (Single):
  poll → FAIL → DLQ → ack → commit   ← 메시지는 DLQ에서 관리

DLQ 전송 실패 (Single):
  poll → FAIL → DLQ FAIL → ack 안 함 → 재배달
```

---

## 3. 재처리 시나리오

### 3-1. 전제 조건

오프셋 되감기를 통한 재처리는 다음 조건이 충족되어야 안전하다:

1. **Consumer 멱등성**: `EventHandled` 테이블에 이미 처리된 eventId가 있으면 skip됨
2. **Consumer 중지 상태**: 되감기 전 반드시 Consumer를 중지해야 함
3. **Kafka 메시지 보존**: `log.retention.hours`(기본 168시간=7일) 내의 메시지만 재처리 가능

### 3-2. 시나리오 A — Consumer 버그로 인한 잘못된 처리 복구

**상황**: 배포 후 Consumer 로직 버그 발견. 특정 시점 이후 처리된 결과가 잘못됨.

```bash
# 1. Consumer 중지 (배포 또는 스케일 다운)

# 2. 잘못된 처리 데이터 보정
#    - EventHandled 테이블에서 재처리 대상 eventId 삭제
#    - 잘못 집계된 ProductMetrics 보정
#    ※ EventHandled를 삭제하지 않으면 skip되어 재처리 안 됨

# 3. 버그 수정 및 배포

# 4-a. 시각 기준 되감기 (가장 일반적)
kafka-consumer-groups.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --group loopers-default-consumer \
  --topic catalog-events \
  --reset-offsets \
  --to-datetime 2026-03-25T15:00:00.000 \
  --execute

# 4-b. 상대적 되감기 (최근 N건만)
kafka-consumer-groups.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --group loopers-default-consumer \
  --topic order-events \
  --reset-offsets \
  --shift-by -500 \
  --execute

# 5. Consumer 재시작
```

**주의사항**:
- `EventHandled` 삭제 없이 되감기하면 모든 메시지가 skip되어 재처리 효과 없음
- `ProductMetrics`의 `@Version` 기반 OptimisticLock이 있으므로, 보정 시 version도 함께 고려

### 3-3. 시나리오 B — DLQ 메시지 재처리

**상황**: DLQ에 쌓인 메시지를 원인 해결 후 재처리.

```bash
# 1. DLQ 메시지 확인
kafka-console-consumer.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic catalog-events.dlq \
  --from-beginning \
  --max-messages 10

# 2. 원인 분석 및 수정

# 3-a. DLQ 메시지를 원본 토픽으로 재발행 (권장)
#    별도 스크립트 또는 Admin API로 DLQ → 원본 토픽 전송
#    Consumer 멱등성이 보장되므로 안전

# 3-b. 수동 DB 보정 (DLQ 메시지가 소량일 때)
#    DLQ 메시지의 payload를 읽고 직접 DB에 반영
```

**DLQ 토픽 목록**:
| 원본 토픽 | DLQ 토픽 |
|-----------|----------|
| `catalog-events` | `catalog-events.dlq` |
| `order-events` | `order-events.dlq` |
| `coupon-issue-requests` | `coupon-issue-requests.dlq` |

### 3-4. 시나리오 C — 선착순 쿠폰 발급 실패 재처리

**상황**: DB 일시 장애로 쿠폰 발급 실패, DLQ에 적재됨.

```bash
# 1. DLQ 메시지 확인
kafka-console-consumer.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic coupon-issue-requests.dlq \
  --from-beginning

# 2. Redis 카운터 확인
redis-cli GET coupon:fcfs:{couponId}:count

# 3. 판단
#    - Redis 카운터 > 실제 DB 발급 수: DECR 보상이 실패한 상태
#    - 카운터 보정: redis-cli SET coupon:fcfs:{couponId}:count {실제발급수}

# 4. DLQ 메시지를 coupon-issue-requests 토픽으로 재발행
#    ※ EventHandled에 없는 eventId만 재처리됨
#    ※ Redis INCR이 다시 수행되므로 카운터 보정이 선행되어야 함
```

**주의사항**:
- 쿠폰 발급은 Redis INCR → DB TX 순서로 진행되므로, 재처리 시 Redis 카운터가 다시 증가함
- 반드시 Redis 카운터를 실제 발급 수로 보정한 뒤 재처리해야 초과 발급/수량 누수를 방지

---

## 4. 장애 복구 절차

### 4-1. Kafka 브로커 장애

**증상**: Producer에서 `TimeoutException`, Consumer에서 리밸런싱 발생

```
확인:
  kafka-topics.sh --describe --topic order-events \
    --bootstrap-server $BOOTSTRAP_SERVERS
  → ISR 목록에서 장애 브로커가 빠졌는지 확인

대응:
  1. ISR >= min.insync.replicas 이면:
     → 리더 전환 자동 발생. 서비스 영향 최소화.
     → Outbox Publisher가 send 실패 시 자동 재시도 (5회까지).

  2. ISR < min.insync.replicas 이면:
     → Producer가 acks=all을 만족하지 못해 발행 실패.
     → Outbox 이벤트가 PENDING 상태로 누적.
     → 브로커 복구 후 OutboxPublisher가 자동으로 미발행 이벤트 발행.
     → FAILED 상태(5회 초과)인 이벤트는 수동 확인 필요:
        SELECT * FROM outbox_events WHERE status = 'FAILED';
```

### 4-2. Consumer Lag 폭발

**증상**: Consumer 처리량 < 유입량. Lag이 지속적으로 증가.

```
확인:
  kafka-consumer-groups.sh --describe \
    --group loopers-default-consumer \
    --bootstrap-server $BOOTSTRAP_SERVERS
  → CURRENT-OFFSET, LOG-END-OFFSET, LAG 확인

대응 (단계적):
  1. 원인 파악: DB 응답 지연? OptimisticLock 충돌 증가? 외부 서비스 지연?
  2. Consumer 스케일아웃: 파티션 수 이내에서 concurrency 증가
     → 현재 Batch Consumer concurrency=3, 파티션 수 확인 후 조정
  3. 파티션 수 증설 (최후 수단):
     → 기존 파티션 키 라우팅이 변경되므로 순서 보장에 영향
     → 증설 전 Consumer 중지 권장
```

### 4-3. Outbox 미발행 이벤트 누적

**증상**: outbox_events 테이블에 PENDING 상태가 지속적으로 쌓임

```
확인:
  SELECT status, COUNT(*) FROM outbox_events GROUP BY status;
  SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 10;

원인별 대응:
  1. Kafka 연결 불가:
     → 브로커 상태 확인 (4-1 참조)
     → OutboxPublisher 로그에서 연속 실패 확인

  2. OutboxPublisher 비활성화:
     → outbox.publisher.enabled 설정 확인
     → test 프로파일에서는 기본 비활성화

  3. 특정 이벤트만 반복 실패:
     → FAILED 상태 이벤트 확인
     → payload 직렬화 문제, 토픽 미존재 등 확인
     → 수동 보정 후 status를 PENDING으로 변경하여 재발행:
       UPDATE outbox_events SET status = 'PENDING', retry_count = 0
       WHERE id = {id} AND status = 'FAILED';
```

### 4-4. 메시지 유실 의심

**증상**: 비즈니스 데이터는 있으나 Consumer 측 처리 결과가 없음

```
확인 절차:
  1. outbox_events 테이블에서 해당 이벤트 상태 확인
     SELECT * FROM outbox_events WHERE aggregate_id = {id};
     → PENDING: 아직 발행 안 됨 (Outbox Publisher 지연 또는 장애)
     → PUBLISHED: Kafka에 발행됨. Consumer 측 확인 필요.
     → FAILED: 발행 실패. 수동 재발행 필요.

  2. Consumer 측 EventHandled 테이블 확인
     SELECT * FROM event_handled WHERE event_id = '{eventId}';
     → 존재: 처리 완료됨. 비즈니스 결과 데이터 확인.
     → 미존재: 처리 안 됨. DLQ 확인.

  3. DLQ 확인
     kafka-console-consumer.sh --topic {topic}.dlq --from-beginning \
       --bootstrap-server $BOOTSTRAP_SERVERS | grep {eventId}

  4. ProductViewedEvent의 경우:
     → Outbox를 거치지 않으므로 발행 추적 불가 (fire-and-forget)
     → 유실 허용 설계이므로 별도 보정 불필요
```

### 4-5. Redis-DB 카운터 불일치 (선착순 쿠폰)

**증상**: Redis 카운터와 실제 DB 발급 수가 다름

```
확인:
  Redis: redis-cli GET coupon:fcfs:{couponId}:count
  DB:    SELECT issued_count FROM fcfs_coupons WHERE coupon_id = {couponId};
         SELECT COUNT(*) FROM coupon_issue_requests
         WHERE coupon_id = {couponId} AND status = 'SUCCESS';

대응:
  DB가 source of truth. Redis는 gate 역할.

  1. Redis > DB (카운터가 높음 — 발급 가능 수량 누수):
     → DB 실패 후 DECR 보상이 안 된 케이스
     → 보정: redis-cli SET coupon:fcfs:{couponId}:count {DB실제발급수}

  2. Redis < DB (카운터가 낮음 — 초과 발급 위험):
     → 일반적으로 발생하지 않는 상황
     → 즉시 Redis 카운터를 DB 값으로 보정
     → 원인 조사 필요 (수동 DB 조작 등)
```

---

## 5. 모니터링 체크리스트

### 일상 모니터링

| 지표 | 확인 방법 | 임계치 |
|------|-----------|--------|
| Consumer Lag | `kafka-consumer-groups.sh --describe` | 파티션별 Lag > 1000 |
| Outbox PENDING 수 | `SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'` | > 100 (1분 이상 체류) |
| Outbox FAILED 수 | `SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED'` | > 0 |
| DLQ 메시지 수 | 토픽별 offset 확인 | > 0 (알림 발생) |
| Redis-DB 카운터 차이 | 쿠폰별 비교 스크립트 | 차이 > 0 |

### 장애 시 우선순위

```
P0 (즉시 대응):
  - Outbox FAILED 누적 → 메시지 유실 가능
  - DLQ 적재 → 비즈니스 데이터 정합성 깨짐
  - Redis-DB 카운터 불일치 → 쿠폰 초과/누수 발급

P1 (1시간 내 대응):
  - Consumer Lag 지속 증가 → 처리 지연 누적
  - Outbox PENDING 장기 체류 → 이벤트 발행 지연

P2 (일간 확인):
  - Consumer 리밸런싱 빈도 → 설정 튜닝 필요 여부
  - OptimisticLock 재시도 빈도 → 동시성 경합 수준
```

---

## 6. 운영 명령어 Quick Reference

```bash
# Consumer Group 상태 확인
kafka-consumer-groups.sh --describe \
  --group loopers-default-consumer \
  --bootstrap-server $BOOTSTRAP_SERVERS

kafka-consumer-groups.sh --describe \
  --group commerce-streamer-coupon \
  --bootstrap-server $BOOTSTRAP_SERVERS

# 토픽 파티션/ISR 상태
kafka-topics.sh --describe --topic catalog-events \
  --bootstrap-server $BOOTSTRAP_SERVERS

# 오프셋 되감기 (Consumer 중지 후 실행)
kafka-consumer-groups.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --group loopers-default-consumer \
  --topic catalog-events \
  --reset-offsets --to-datetime 2026-03-25T15:00:00.000 \
  --execute

kafka-consumer-groups.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --group commerce-streamer-coupon \
  --topic coupon-issue-requests \
  --reset-offsets --shift-by -100 \
  --execute

# DLQ 메시지 확인
kafka-console-consumer.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic catalog-events.dlq \
  --from-beginning --max-messages 10

kafka-console-consumer.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic order-events.dlq \
  --from-beginning --max-messages 10

kafka-console-consumer.sh \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic coupon-issue-requests.dlq \
  --from-beginning --max-messages 10

# Outbox 상태 확인 (DB)
# SELECT status, COUNT(*) FROM outbox_events GROUP BY status;
# SELECT * FROM outbox_events WHERE status = 'FAILED';

# Redis 쿠폰 카운터 확인
# redis-cli GET coupon:fcfs:{couponId}:count
```

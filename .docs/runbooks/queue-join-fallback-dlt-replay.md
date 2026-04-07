# 대기열 Kafka 폴백 DLT 재처리 런북

`QUEUE_JOIN_FALLBACK_REQUESTED` 복구 소비가 재시도(기본 4회, 백오프)를 모두 소진하면 Spring Kafka RetryableTopic이 **DLT 토픽**으로 넘기고, 애플리케이션 `@DltHandler`가 메트릭·ack·**재발행용 로그**를 남긴다.

## 1. 감지

- Micrometer: `loopers.queue.join.fallback.dlt` (Prometheus에서는 보통 `loopers_queue_join_fallback_dlt_total`) 증가 알람.
- 애플리케이션 로그: 키워드 `queue_join_fallback_dlt`, 레벨 `WARN`.

## 2. 원인 조치(선행)

DLT로 간 메시지를 곧바로 다시 넣으면 동일 실패가 반복될 수 있다. **Redis 가용·네트워크·페이로드 스키마** 등 `WaitingQueueService#joinQueueFromRecovery`가 성공할 조건을 먼저 복구한다.

## 3. DLT 토픽 이름(참고)

`@RetryableTopic` + 기본 접미사일 때 본 토픽이 `queue-join-fallback`이면 DLT는 보통 **`queue-join-fallback-dlt`** 이다. 환경별로 Kafka UI 또는 브로커 메타데이터로 확인한다.

## 4. 권장 재처리: 본 토픽으로 수동 재발행

복구 컨슈머가 소비하는 토픽은 **`queue.fallback.topic-name`**(기본 `queue-join-fallback`)이다.  
발행 형식은 `KafkaQueueJoinFallbackPublisher`와 동일한 **envelope JSON**이어야 하며, 메시지 **키**는 발행 시와 같이 **userId 문자열**을 쓰는 것이 좋다(파티션 일관).

### 4.1 로그에서 페이로드 확보

`onDlt` 로그에 `payloadUtf8=` 뒤에 전체(또는 32KB 초과 시 잘림) JSON이 붙는다. 잘린 경우 브로커/모니터링에서 DLT 원문을 조회한다.

### 4.2 예시(kcat)

토픽·브로커는 환경에 맞게 바꾼다.

```bash
# 값만 보내고 키는 별도 지정이 어려운 경우, 클러스터에 맞는 도구 사용 권장.
echo '<envelope-json-한-줄>' | kcat -P -b localhost:9092 -t queue-join-fallback
```

키를 맞추려면 클라이언트에서 `ProducerRecord`로 key=`userId`, value=envelope 를 보낸다.

### 4.3 envelope 최소 필드

리스너는 다음을 사용한다.

- 최상위 `eventType`: `QUEUE_JOIN_FALLBACK_REQUESTED`
- `data.eventId`, `data.userId`, `data.score`

운영 재발행 시에는 원본 메시지 JSON을 그대로 쓰는 것이 가장 안전하다.

## 5. 자동 재발행

현재 구현은 DLT에서 본 토픽으로 **자동 재전송하지 않는다**. 원인 미해결 시 재시도 루프·부하만 커질 수 있기 때문이다.

## 6. 관련 코드

- 소비·DLT: `QueueJoinFallbackKafkaListener`
- 발행 형식: `KafkaQueueJoinFallbackPublisher#buildEnvelopeJson`
- 도메인 복구: `WaitingQueueService#joinQueueFromRecovery`

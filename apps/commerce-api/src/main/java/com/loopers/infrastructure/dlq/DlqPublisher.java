package com.loopers.infrastructure.dlq;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DLQ Publisher — 처리 실패한 메시지를 DLQ 토픽으로 격리 (commerce-api용)
 *
 * 동기 전송 (.get()):
 *   DLQ 전송도 실패하면 메시지가 영원히 유실된다.
 *   비동기(fire-and-forget)로 하면 DLQ 전송 실패를 감지 못 함.
 *   금전 가치 이벤트(쿠폰)는 DLQ 전송도 반드시 성공해야 함.
 *
 * 헤더:
 *   X-Original-Topic, X-Original-Partition, X-Original-Offset
 *   X-Error-Message, X-Error-Timestamp, X-Retry-Count
 */
@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);
    private static final String DLQ_TOPIC = "pipeline-dlq-v1";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public DlqPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToDlq(ConsumerRecord<Object, Object> record, Exception exception, int retryCount) {
        try {
            String errorMsg = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();

            ProducerRecord<Object, Object> dlqRecord = new ProducerRecord<>(
                    DLQ_TOPIC, null, record.key(), record.value());

            dlqRecord.headers()
                    .add(new RecordHeader("X-Original-Topic",
                            record.topic().getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Original-Partition",
                            String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Original-Offset",
                            String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Error-Message",
                            errorMsg.getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Error-Timestamp",
                            ZonedDateTime.now().toString().getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Retry-Count",
                            String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8)));

            // 동기 전송 — DLQ 유실 방지
            SendResult<Object, Object> result = kafkaTemplate.send(dlqRecord).get(10, TimeUnit.SECONDS);

            var metadata = result.getRecordMetadata();
            log.warn("[DLQ] 메시지 격리 완료 — dlqPartition={}, dlqOffset={}, " +
                            "originalTopic={}, originalPartition={}, originalOffset={}, retryCount={}, error={}",
                    metadata.partition(), metadata.offset(),
                    record.topic(), record.partition(), record.offset(), retryCount, errorMsg);

        } catch (ExecutionException | TimeoutException e) {
            log.error("[DLQ] DLQ 전송 실패! 메시지 유실 위험 — topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DLQ] DLQ 전송 중단 — topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
        }
    }
}

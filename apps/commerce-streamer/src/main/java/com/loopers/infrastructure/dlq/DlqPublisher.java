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
 * DLQ Publisher — 처리 실패한 메시지를 DLQ 토픽으로 격리
 *
 * 동기 전송 (.get()) — DLQ 유실 방지
 * X-Retry-Count 헤더 포함 — 재시도 횟수 추적
 * 전송 결과 메타데이터 로깅 — DLQ 메시지 위치 추적
 */
@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);
    private static final String DLQ_TOPIC = "pipeline-dlq-v1";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public DlqPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToDlq(ConsumerRecord<Object, Object> record, Exception exception) {
        sendToDlq(record, exception, 0);
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
                            "originalTopic={}, partition={}, offset={}, retryCount={}, error={}",
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

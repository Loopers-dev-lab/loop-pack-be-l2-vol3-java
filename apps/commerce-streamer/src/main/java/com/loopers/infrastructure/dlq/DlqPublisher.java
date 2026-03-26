package com.loopers.infrastructure.dlq;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

/**
 * DLQ Publisher — 처리 실패한 메시지를 DLQ 토픽으로 격리
 *
 * 배치 처리 중 개별 레코드가 실패하면 DLQ에 보내고 나머지는 계속 처리.
 * 실패 레코드가 유실되지 않고 DLQ에 보존되어 원인 분석 + 재처리 가능.
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
        try {
            ProducerRecord<Object, Object> dlqRecord = new ProducerRecord<>(
                    DLQ_TOPIC, null,
                    record.key(),
                    record.value()
            );

            dlqRecord.headers()
                    .add(new RecordHeader("X-Original-Topic",
                            record.topic().getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Original-Partition",
                            String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Original-Offset",
                            String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Error-Message",
                            (exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName())
                                    .getBytes(StandardCharsets.UTF_8)))
                    .add(new RecordHeader("X-Error-Timestamp",
                            ZonedDateTime.now().toString().getBytes(StandardCharsets.UTF_8)));

            kafkaTemplate.send(dlqRecord);

            log.warn("[DLQ] 메시지 격리 — originalTopic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), exception.getMessage());

        } catch (Exception e) {
            log.error("[DLQ] DLQ 전송마저 실패 — topic={}, partition={}, offset={}, dlqError={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
        }
    }
}

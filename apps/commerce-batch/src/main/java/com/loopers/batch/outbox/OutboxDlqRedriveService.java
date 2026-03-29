package com.loopers.batch.outbox;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.TopicPartition;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxDlqRedriveService {
    private static final String HEADER_REDRIVE_ATTEMPT = "x-redrive-attempt";
    private static final String HEADER_REDRIVE_RESULT = "x-redrive-result";
    private static final String RESULT_PARKED = "parked";

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxDlqRedriveProperties properties;
    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter parkedCounter;

    public OutboxDlqRedriveService(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            OutboxDlqRedriveProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.successCounter = meterRegistry.counter("kafka.outbox.dlq.redrive.success");
        this.failedCounter = meterRegistry.counter("kafka.outbox.dlq.redrive.failed");
        this.parkedCounter = meterRegistry.counter("kafka.outbox.dlq.redrive.parked");
    }

    public int redriveOnce(int batchSize) {
        int maxBatchSize = Math.max(1, batchSize);
        int successCount = 0;
        Duration pollTimeout = properties.pollTimeout();
        long sendAckTimeoutMs = properties.sendAckTimeout().toMillis();

        Map<String, Object> consumerConfig = new HashMap<>(consumerFactory.getConfigurationProperties());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, properties.groupId());
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<Object, Object> consumer = new KafkaConsumer<>(consumerConfig)) {
            consumer.subscribe(Collections.singletonList(properties.dlqTopic()));
            ConsumerRecords<Object, Object> records = consumer.poll(pollTimeout);
            if (records.isEmpty()) {
                // 최초 poll은 group join/assignment에 사용될 수 있어 한 번 더 조회한다.
                records = consumer.poll(pollTimeout);
            }

            for (ConsumerRecord<Object, Object> record : records) {
                if (successCount >= maxBatchSize) {
                    break;
                }
                int currentAttempt = extractAttempt(record.headers().lastHeader(HEADER_REDRIVE_ATTEMPT));
                if (currentAttempt >= properties.maxAttempts()) {
                    parkAndCommit(record, consumer, currentAttempt);
                    continue;
                }
                try {
                    ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(
                            properties.sourceTopic(),
                            null,
                            record.key(),
                            record.value()
                    );
                    record.headers().forEach(h -> producerRecord.headers().add(h));
                    producerRecord.headers().remove(HEADER_REDRIVE_ATTEMPT);
                    producerRecord.headers().add(new RecordHeader(
                            HEADER_REDRIVE_ATTEMPT,
                            String.valueOf(currentAttempt + 1).getBytes(StandardCharsets.UTF_8)
                    ));

                    kafkaTemplate.send(producerRecord)
                            .get(sendAckTimeoutMs, TimeUnit.MILLISECONDS);

                    TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                    OffsetAndMetadata offset = new OffsetAndMetadata(record.offset() + 1);
                    consumer.commitSync(Map.of(tp, offset));
                    successCount++;
                    successCounter.increment();
                } catch (Exception ignored) {
                    // 재발행 실패 시 오프셋을 커밋하지 않아 다음 주기에 재시도한다.
                    failedCounter.increment();
                }
            }
        }

        return successCount;
    }

    private void parkAndCommit(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer, int attempt) {
        try {
            ProducerRecord<Object, Object> parkRecord = new ProducerRecord<>(
                    properties.parkingTopic(),
                    null,
                    record.key(),
                    record.value()
            );
            record.headers().forEach(h -> parkRecord.headers().add(h));
            parkRecord.headers().remove(HEADER_REDRIVE_RESULT);
            parkRecord.headers().add(new RecordHeader(
                    HEADER_REDRIVE_RESULT,
                    RESULT_PARKED.getBytes(StandardCharsets.UTF_8)
            ));
            parkRecord.headers().remove(HEADER_REDRIVE_ATTEMPT);
            parkRecord.headers().add(new RecordHeader(
                    HEADER_REDRIVE_ATTEMPT,
                    String.valueOf(attempt).getBytes(StandardCharsets.UTF_8)
            ));

            kafkaTemplate.send(parkRecord).get(properties.sendAckTimeout().toMillis(), TimeUnit.MILLISECONDS);
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            OffsetAndMetadata offset = new OffsetAndMetadata(record.offset() + 1);
            consumer.commitSync(Map.of(tp, offset));
            parkedCounter.increment();
        } catch (Exception e) {
            failedCounter.increment();
        }
    }

    private static int extractAttempt(Header attemptHeader) {
        if (attemptHeader == null || attemptHeader.value() == null) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(attemptHeader.value(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return 0;
        }
    }
}

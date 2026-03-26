package com.loopers.batch.outbox;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxDlqRedriveService {

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxDlqRedriveProperties properties;

    public OutboxDlqRedriveService(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            OutboxDlqRedriveProperties properties
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
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
                try {
                    ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(
                            properties.sourceTopic(),
                            null,
                            record.key(),
                            record.value()
                    );
                    record.headers().forEach(h -> producerRecord.headers().add(h));

                    kafkaTemplate.send(producerRecord)
                            .get(sendAckTimeoutMs, TimeUnit.MILLISECONDS);

                    TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                    OffsetAndMetadata offset = new OffsetAndMetadata(record.offset() + 1);
                    consumer.commitSync(Map.of(tp, offset));
                    successCount++;
                } catch (Exception ignored) {
                    // 재발행 실패 시 오프셋을 커밋하지 않아 다음 주기에 재시도한다.
                }
            }
        }

        return successCount;
    }
}

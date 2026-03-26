package com.loopers.batch.outbox;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false",
        "outbox.dlq-redrive.enabled=false",
        "outbox.dlq-redrive.group-id=dlq-redrive-it",
        "outbox.dlq-redrive.max-attempts=3"
})
@Import(MySqlTestContainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"product-events", "product-events.DLQ", "product-events.DLQ.PARK"})
class OutboxDlqRedriveIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private OutboxDlqRedriveService outboxDlqRedriveService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("DLQ 레코드를 원본 토픽으로 재발행하고 오프셋을 커밋한다.")
    void redriveOnce_shouldRepublishAndCommitOffset() {
        ProducerRecord<Object, Object> dlqRecord = new ProducerRecord<>(
                "product-events.DLQ",
                "1",
                "{\"eventId\":\"evt-redrive-1\",\"eventType\":\"PRODUCT_LIKE_CHANGED\"}"
        );
        dlqRecord.headers().add("eventId", "evt-redrive-1".getBytes(StandardCharsets.UTF_8));
        dlqRecord.headers().add("eventType", "PRODUCT_LIKE_CHANGED".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(dlqRecord);
        kafkaTemplate.flush();

        int redriven = outboxDlqRedriveService.redriveOnce(10);
        assertThat(redriven).isEqualTo(1);
        assertThat(counterValue("kafka.outbox.dlq.redrive.success")).isEqualTo(1.0);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("redrive-read", "false", embeddedKafkaBroker),
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "product-events");

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "product-events");
        assertThat(record.key()).isEqualTo("1");
        Header eventIdHeader = record.headers().lastHeader("eventId");
        assertThat(eventIdHeader).isNotNull();
        assertThat(new String(eventIdHeader.value(), StandardCharsets.UTF_8)).isEqualTo("evt-redrive-1");
        consumer.close();

        int secondRun = outboxDlqRedriveService.redriveOnce(10);
        assertThat(secondRun).isEqualTo(0);
    }

    @Test
    @DisplayName("재처리 한도에 도달한 메시지는 PARK 토픽으로 격리한다.")
    void redriveOnce_whenMaxAttemptReached_shouldParkMessage() {
        ProducerRecord<Object, Object> dlqRecord = new ProducerRecord<>(
                "product-events.DLQ",
                "1",
                "{\"eventId\":\"evt-redrive-park\",\"eventType\":\"PRODUCT_LIKE_CHANGED\"}"
        );
        dlqRecord.headers().add("eventId", "evt-redrive-park".getBytes(StandardCharsets.UTF_8));
        dlqRecord.headers().add("x-redrive-attempt", "3".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(dlqRecord);
        kafkaTemplate.flush();

        int redriven = outboxDlqRedriveService.redriveOnce(10);
        assertThat(redriven).isEqualTo(0);
        assertThat(counterValue("kafka.outbox.dlq.redrive.parked")).isEqualTo(1.0);

        Consumer<String, String> parkConsumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("redrive-park-read", "false", embeddedKafkaBroker),
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(parkConsumer, "product-events.DLQ.PARK");
        ConsumerRecord<String, String> parked = KafkaTestUtils.getSingleRecord(parkConsumer, "product-events.DLQ.PARK");
        Header resultHeader = parked.headers().lastHeader("x-redrive-result");
        assertThat(resultHeader).isNotNull();
        assertThat(new String(resultHeader.value(), StandardCharsets.UTF_8)).isEqualTo("parked");
        parkConsumer.close();
    }

    private double counterValue(String name) {
        if (meterRegistry.find(name).counter() == null) {
            return 0.0;
        }
        return meterRegistry.find(name).counter().count();
    }
}

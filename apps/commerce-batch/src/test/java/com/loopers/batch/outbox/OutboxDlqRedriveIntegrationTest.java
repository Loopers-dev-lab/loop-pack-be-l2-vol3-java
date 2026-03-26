package com.loopers.batch.outbox;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
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
        "outbox.dlq-redrive.group-id=dlq-redrive-it"
})
@Import(MySqlTestContainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"product-events", "product-events.DLQ"})
class OutboxDlqRedriveIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private OutboxDlqRedriveService outboxDlqRedriveService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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
}

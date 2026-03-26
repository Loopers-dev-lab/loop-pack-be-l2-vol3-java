package com.loopers.batch.outbox;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.infrastructure.outbox.OutboxEventModel;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"catalog-events"})
class OutboxRelayIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("미발행 Outbox를 폴링해 Kafka로 전송하고 published로 마킹한다.")
    void relayOnce_shouldSendAndMarkPublished() {
        OutboxEventModel pending = outboxJpaRepository.save(OutboxEventModel.pending(
                "event-1",
                "catalog-events",
                "1",
                "TEST_EVENT",
                Instant.now(),
                "{\"hello\":\"world\"}"
        ));

        int relayed = outboxRelayService.relayOnce(10);

        assertThat(relayed).isEqualTo(1);
        OutboxEventModel updated = outboxJpaRepository.findById(pending.getId()).orElseThrow();
        assertThat(updated.isPublished()).isTrue();
        assertThat(updated.getPublishedAt()).isNotNull();

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("relay-test", "false", embeddedKafkaBroker),
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "catalog-events");

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "catalog-events");
        assertThat(record.key()).isEqualTo("1");
        assertThat(record.value()).contains("eventId");
        assertThat(record.value()).contains("event-1");
        assertThat(record.value()).contains("eventType");
        assertThat(record.value()).contains("TEST_EVENT");
        assertThat(record.value()).contains("hello");

        Header eventIdHeader = record.headers().lastHeader("eventId");
        Header eventTypeHeader = record.headers().lastHeader("eventType");
        assertThat(eventIdHeader).isNotNull();
        assertThat(eventTypeHeader).isNotNull();
        assertThat(new String(eventIdHeader.value())).isEqualTo("event-1");
        assertThat(new String(eventTypeHeader.value())).isEqualTo("TEST_EVENT");

        consumer.close();
    }
}


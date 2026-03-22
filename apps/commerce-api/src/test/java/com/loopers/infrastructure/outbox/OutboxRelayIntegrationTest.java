package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(KafkaTestContainersConfig.class)
@DisplayName("OutboxRelayScheduler 통합 테스트")
class OutboxRelayIntegrationTest {

    static final String TEST_TOPIC = "catalog-events";

    @Autowired
    private OutboxRelayScheduler scheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("PENDING 상태의 Outbox를 relay() 호출 시 Kafka에 발행하고 PUBLISHED로 변경한다")
    void relay_publishesPendingOutbox_andMarksPublished() throws Exception {
        OutboxModel outbox = outboxRepository.save(
                OutboxModel.create("product", "product-1", "LikedEvent", TEST_TOPIC, "{\"productId\":\"product-1\"}")
        );

        scheduler.relay();

        OutboxModel saved = outboxRepository.findPendingWithLimit(10).stream()
                .filter(o -> o.getId().equals(outbox.getId()))
                .findFirst()
                .orElse(null);

        assertThat(saved).isNull();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(TEST_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        String bootstrapServers = System.getProperty("spring.kafka.bootstrap-servers");
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-relay-consumer-" + System.currentTimeMillis(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}

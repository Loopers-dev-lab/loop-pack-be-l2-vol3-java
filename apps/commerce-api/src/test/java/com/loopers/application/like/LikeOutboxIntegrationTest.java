package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.outbox.OutboxRelayScheduler;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(KafkaTestContainersConfig.class)
@DisplayName("LikeApp Outbox 통합 테스트")
class LikeOutboxIntegrationTest {

    static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    static final String PRODUCT_ID = "prod-outbox-1";
    static final Long MEMBER_ID = 1L;

    @Autowired
    private LikeApp likeApp;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        brandService.createBrand("brand-outbox", "Test Brand");
        productService.createProduct(PRODUCT_ID, "brand-outbox", "Test Product", new BigDecimal("10000"), 100);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("addLike() 호출 시 PENDING Outbox가 생성되고 relay() 후 catalog-events에 발행된다")
    void addLike_createsOutbox_andRelayPublishesToKafka() throws Exception {
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);

        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("LikedEvent");
        assertThat(pending.get(0).getAggregateId()).isEqualTo(PRODUCT_ID);
        assertThat(pending.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

        outboxRelayScheduler.compensate();

        assertThat(outboxRepository.findPendingWithLimit(10)).isEmpty();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(CATALOG_EVENTS_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("removeLike() 호출 시 PENDING Outbox가 생성되고 relay() 후 catalog-events에 발행된다")
    void removeLike_createsOutbox_andRelayPublishesToKafka() throws Exception {
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);
        likeApp.removeLike(MEMBER_ID, PRODUCT_ID);

        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
        assertThat(pending).hasSize(2);
        assertThat(pending.stream().anyMatch(o -> "LikeRemovedEvent".equals(o.getEventType()))).isTrue();

        outboxRelayScheduler.compensate();

        assertThat(outboxRepository.findPendingWithLimit(10)).isEmpty();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(CATALOG_EVENTS_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("soft-delete 복원(like → unlike → like) 경로에서도 Outbox가 생성된다")
    void addLike_afterRestore_createsOutbox() {
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);
        likeApp.removeLike(MEMBER_ID, PRODUCT_ID);
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);

        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
        assertThat(pending).hasSize(3);

        long likedEventCount = pending.stream()
                .filter(o -> "LikedEvent".equals(o.getEventType()))
                .count();
        assertThat(likedEventCount).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 좋아요 상태에서 중복 addLike() 시 Outbox가 추가 생성되지 않는다")
    void addLike_duplicate_doesNotCreateExtraOutbox() {
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);
        likeApp.addLike(MEMBER_ID, PRODUCT_ID);

        assertThat(outboxRepository.findPendingWithLimit(10)).hasSize(1);
    }

    @Test
    @DisplayName("좋아요가 없는 상태에서 removeLike() 호출 시 Outbox가 생성되지 않는다")
    void removeLike_notExists_doesNotCreateOutbox() {
        likeApp.removeLike(MEMBER_ID, PRODUCT_ID);

        assertThat(outboxRepository.findPendingWithLimit(10)).isEmpty();
    }

    private KafkaConsumer<String, String> createConsumer() {
        String bootstrapServers = System.getProperty("spring.kafka.bootstrap-servers");
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-like-outbox-" + System.currentTimeMillis(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}

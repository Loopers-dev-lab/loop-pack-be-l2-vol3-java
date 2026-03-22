package com.loopers.application.order;

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
@DisplayName("OrderApp Outbox 통합 테스트")
class OrderOutboxIntegrationTest {

    static final String ORDER_EVENTS_TOPIC = "order-events";
    static final String PRODUCT_ID = "prod-order-1";
    static final Long MEMBER_ID = 1L;

    @Autowired
    private OrderApp orderApp;

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
        brandService.createBrand("brand-order", "Order Brand");
        productService.createProduct(PRODUCT_ID, "brand-order", "Test Product", new BigDecimal("50000"), 100);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("createOrder() 호출 시 PENDING Outbox가 생성되고 relay() 후 order-events에 발행된다")
    void createOrder_createsOutbox_andRelayPublishesToKafka() throws Exception {
        orderApp.createOrder(MEMBER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 1)));

        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("OrderCreatedEvent");
        assertThat(pending.get(0).getAggregateType()).isEqualTo("order");
        assertThat(pending.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

        outboxRelayScheduler.relay();

        assertThat(outboxRepository.findPendingWithLimit(10)).isEmpty();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(ORDER_EVENTS_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("createOrder() payload는 thin event 구조(items 미포함)로 생성된다")
    void createOrder_outboxPayload_isThinEvent() {
        orderApp.createOrder(MEMBER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 2)));

        OutboxModel outbox = outboxRepository.findPendingWithLimit(10).get(0);
        assertThat(outbox.getPayload()).contains("OrderCreatedEvent");
        assertThat(outbox.getPayload()).contains("orderDbId");
        assertThat(outbox.getPayload()).doesNotContain("items");
        assertThat(outbox.getPayload()).doesNotContain("productName");
    }

    @Test
    @DisplayName("주문 생성 시 쿠폰 없이도 Outbox가 정상 생성된다")
    void createOrder_withoutCoupon_createsOutbox() {
        orderApp.createOrder(MEMBER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 1)));

        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getTopic()).isEqualTo(ORDER_EVENTS_TOPIC);
    }

    private KafkaConsumer<String, String> createConsumer() {
        String bootstrapServers = System.getProperty("spring.kafka.bootstrap-servers");
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-order-outbox-" + System.currentTimeMillis(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}

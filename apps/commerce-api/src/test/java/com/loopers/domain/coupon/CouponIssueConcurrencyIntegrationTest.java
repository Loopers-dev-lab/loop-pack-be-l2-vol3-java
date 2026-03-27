package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.application.coupon.IssueOwnedCouponUseCase;
import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.ConcurrentTestHelper.ConcurrentResult;
import com.loopers.support.error.CoreException;

@DisplayName("쿠폰 발급 동시성 테스트 (Redis + Kafka 비동기 발급)")
class CouponIssueConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConcurrencyIntegrationTest.class);
    private static final String TOPIC = "coupon-issue-v1";

    @Autowired
    private IssueOwnedCouponUseCase issueOwnedCouponUseCase;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponStockManager couponStockManager;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Long couponId;

    @BeforeEach
    void setUp() {
        var coupon = couponService.create(
                new CouponTerms("선착순 쿠폰", CouponType.FIXED, 5000L, null, 10000L,
                        ZonedDateTime.now().plusDays(30), 100)
        );
        couponId = coupon.getId();
        couponStockManager.initialize(couponId, coupon.getTotalQuantity(), coupon.getExpiredAt());
    }

    @DisplayName("100장 쿠폰에 200명이 동시 요청하면, 정확히 100건의 Kafka 이벤트가 발행된다.")
    @Test
    void exactly100EventsPublished_when200ConcurrentRequests() throws InterruptedException {
        // arrange
        int totalQuantity = 100;
        int threadCount = 200;
        AtomicLong userIdGenerator = new AtomicLong(1);

        // Kafka Consumer 준비 — 테스트 시작 전에 구독하여 메시지 유실 방지
        List<ConsumerRecord<String, String>> kafkaMessages;

        try (KafkaConsumer<String, String> consumer = createTestConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            // 초기 poll로 파티션 할당 대기
            consumer.poll(Duration.ofSeconds(2));

            // act
            long startNanos = System.nanoTime();

            ConcurrentResult result = ConcurrentTestHelper.executeConcurrently(threadCount, () ->
                    issueOwnedCouponUseCase.execute(couponId, userIdGenerator.getAndIncrement())
            );

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // Kafka 메시지 수집 (비동기 발행 완료 대기)
            kafkaMessages = pollMessages(consumer, totalQuantity, Duration.ofSeconds(10));

            // Redis 검증
            String redisStock = redisTemplate.opsForValue().get("coupon:" + couponId + ":stock");
            Long redisUsersSize = redisTemplate.opsForSet().size("coupon:" + couponId + ":users");

            // assert
            assertAll(
                    () -> assertThat(result.successCount())
                            .as("성공 수")
                            .isEqualTo(totalQuantity),
                    () -> assertThat(result.failCount())
                            .as("실패 수")
                            .isEqualTo(threadCount - totalQuantity),
                    () -> assertThat(redisStock)
                            .as("Redis 잔여 수량")
                            .isEqualTo("0"),
                    () -> assertThat(redisUsersSize)
                            .as("Redis coupon users size")
                            .isEqualTo((long) totalQuantity),
                    () -> assertThat(kafkaMessages)
                            .as("Kafka 발행 메시지 수")
                            .hasSize(totalQuantity)
            );

            // 비기능 요구사항: 예외 유형별 카운터
            Map<String, Long> exceptionCounts = result.exceptions().stream()
                    .collect(Collectors.groupingBy(
                            e -> e instanceof CoreException ce ? ce.getErrorType().name() : e.getClass().getSimpleName(),
                            Collectors.counting()
                    ));

            log.info("=== Redis + Kafka 비동기 발급 동시성 테스트 결과 ===");
            log.info("소요 시간: {}ms", elapsedMs);
            log.info("성공: {} / 실패: {}", result.successCount(), result.failCount());
            log.info("Kafka 메시지: {}건", kafkaMessages.size());
            log.info("예외 유형: {}", exceptionCounts);
        }
    }

    private KafkaConsumer<String, String> createTestConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-coupon-issue-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        return new KafkaConsumer<>(props);
    }

    private List<ConsumerRecord<String, String>> pollMessages(
            KafkaConsumer<String, String> consumer,
            int expectedCount,
            Duration timeout
    ) {
        List<ConsumerRecord<String, String>> messages = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (messages.size() < expectedCount && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.topic().equals(TOPIC)) {
                    messages.add(record);
                }
            }
        }
        return messages;
    }
}

package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
class CouponIssueConsumerConcurrencyTest {

    @Autowired
    private CouponIssueConsumer consumer;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private CouponIssueRequestJpaRepository requestRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("10개 수량의 쿠폰에 50개의 요청을 동시에 처리하면 발급 수량이 초과되지 않는다.")
    @Test
    void issuedCountDoesNotExceedTotalQuantity_whenProcessedConcurrently() throws InterruptedException {
        // Arrange
        int totalQuantity = 10;
        int requestCount = 50;

        Coupon coupon = couponRepository.save(new Coupon("선착순 동시성 테스트 쿠폰", totalQuantity));

        List<ConsumerRecord<Object, Object>> records = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            CouponIssueRequest request = requestRepository.save(new CouponIssueRequest(coupon.getId(), (long) (i + 1)));
            records.add(buildRecord(coupon.getId(), request.getId(), (long) (i + 1), i));
        }

        // Act — 50개 스레드가 동시에 각 요청을 처리
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        Acknowledgment ack = mock(Acknowledgment.class);

        for (ConsumerRecord<Object, Object> record : records) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    consumer.consume(List.of(record), ack);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert — 총 발급 수량이 제한을 초과하지 않아야 한다
        long issuedCount = issuedCouponRepository.countByCouponId(coupon.getId());
        assertThat(issuedCount).isLessThanOrEqualTo(totalQuantity);
    }

    private ConsumerRecord<Object, Object> buildRecord(Long couponId, Long requestId, Long userId, int offset) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "COUPON_ISSUE_REQUESTED",
                "data", Map.of("requestId", requestId, "couponId", couponId, "userId", userId)
            );
            byte[] value = objectMapper.writeValueAsBytes(payload);
            return new ConsumerRecord<>("coupon.issue.requests", 0, offset, couponId.toString(), value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.loopers.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.consumer.CouponIssueConsumer;
import com.loopers.domain.fcfs.CouponIssueRequestStatus;
import com.loopers.event.AggregateType;
import com.loopers.event.CouponIssueRequestedEvent;
import com.loopers.event.EventEnvelope;
import com.loopers.event.EventType;
import com.loopers.infrastructure.persistence.jpa.fcfs.CouponIssueRequestJpaEntity;
import com.loopers.infrastructure.persistence.jpa.fcfs.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsCouponJpaEntity;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsCouponJpaRepository;
import com.loopers.infrastructure.persistence.jpa.fcfs.FcfsIssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("선착순 쿠폰 발급 동시성 테스트")
class FcfsCouponIssueConcurrencyTest {

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private FcfsCouponJpaRepository fcfsCouponJpaRepository;

    @Autowired
    private FcfsIssuedCouponJpaRepository fcfsIssuedCouponJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long couponId;

    @BeforeEach
    void setUp() {
        // 100개 수량의 선착순 쿠폰 생성
        couponId = insertFcfsCoupon("선착순 100명 쿠폰", 100, 0);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("선착순 100명 쿠폰 발급 시 수량 초과가 발생하지 않는다")
    void 선착순_100명_쿠폰_발급_시_수량_초과가_발생하지_않는다() throws Exception {
        // Arrange: 150명의 요청 준비
        int requestCount = 150;
        int totalQuantity = 100;

        // 150개의 발급 요청 생성 (각기 다른 userId)
        for (int i = 1; i <= requestCount; i++) {
            String requestId = "request-" + i;
            insertCouponIssueRequest(requestId, couponId, (long) i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act: 동시에 150개의 이벤트 처리
        for (int i = 1; i <= requestCount; i++) {
            final long userId = i;
            final String requestId = "request-" + userId;
            executor.submit(() -> {
                try {
                    EventEnvelope envelope = createEventEnvelope(requestId, couponId, userId);
                    couponIssueConsumer.processEvent(envelope);

                    // 발급 결과 확인
                    CouponIssueRequestJpaEntity result = couponIssueRequestJpaRepository
                        .findByRequestId(requestId).orElseThrow();
                    if (result.getStatus() == CouponIssueRequestStatus.ISSUED) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 정확히 100명만 발급 성공
        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(failCount.get()).isEqualTo(requestCount - totalQuantity);

        // 발급된 쿠폰 수 확인
        long issuedCount = fcfsIssuedCouponJpaRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(totalQuantity);

        // 쿠폰의 issued_count 확인
        FcfsCouponJpaEntity updatedCoupon = fcfsCouponJpaRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedCount()).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("같은 유저가 중복 발급 요청하면 한 번만 발급된다")
    void 같은_유저가_중복_발급_요청하면_한_번만_발급된다() throws Exception {
        // Arrange: 같은 userId로 10개의 중복 요청 생성
        Long userId = 1L;
        int duplicateRequestCount = 10;

        for (int i = 1; i <= duplicateRequestCount; i++) {
            String requestId = "duplicate-request-" + i;
            insertCouponIssueRequest(requestId, couponId, userId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(duplicateRequestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act: 동시에 10개의 중복 요청 처리
        for (int i = 1; i <= duplicateRequestCount; i++) {
            final String requestId = "duplicate-request-" + i;
            executor.submit(() -> {
                try {
                    EventEnvelope envelope = createEventEnvelope(requestId, couponId, userId);
                    couponIssueConsumer.processEvent(envelope);

                    // 발급 결과 확인
                    CouponIssueRequestJpaEntity result = couponIssueRequestJpaRepository
                        .findByRequestId(requestId).orElseThrow();
                    if (result.getStatus() == CouponIssueRequestStatus.ISSUED) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 1번만 발급 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(duplicateRequestCount - 1);

        // 발급된 쿠폰 수 확인
        long issuedCount = fcfsIssuedCouponJpaRepository.countByCouponId(couponId);
        assertThat(issuedCount).isEqualTo(1);

        // 쿠폰의 issued_count 확인
        FcfsCouponJpaEntity updatedCoupon = fcfsCouponJpaRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedCount()).isEqualTo(1);
    }

    private Long insertFcfsCoupon(String name, int totalQuantity, int issuedCount) {
        jdbcTemplate.update(
            "INSERT INTO fcfs_coupon (name, total_quantity, issued_count, created_at) VALUES (?, ?, ?, NOW())",
            name, totalQuantity, issuedCount
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertCouponIssueRequest(String requestId, Long couponId, Long userId) {
        jdbcTemplate.update(
            "INSERT INTO coupon_issue_request (request_id, coupon_id, user_id, status, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'PENDING', NOW(), NOW())",
            requestId, couponId, userId
        );
    }

    private EventEnvelope createEventEnvelope(String requestId, Long couponId, Long userId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestedEvent event = CouponIssueRequestedEvent.of(
            eventId, requestId, couponId, userId
        );
        String payload = objectMapper.writeValueAsString(event);

        return new EventEnvelope(
            eventId,
            EventType.COUPON_ISSUE_REQUESTED.name(),
            AggregateType.COUPON.name(),
            String.valueOf(couponId),
            Instant.now(),
            payload
        );
    }
}

package com.loopers.concurrency;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    private void insertCoupon(Long couponId, int maxIssueCount) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO coupons (id, max_issue_count, issued_count, expired_at) " +
                    "VALUES (:id, :max, 0, :expired)")
                    .setParameter("id", couponId)
                    .setParameter("max", maxIssueCount)
                    .setParameter("expired", LocalDateTime.now().plusDays(7))
                    .executeUpdate();
        });
    }

    private void insertPendingRequest(String eventId, Long couponId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO coupon_issue_requests (event_id, coupon_id, user_id, status, created_at) " +
                    "VALUES (:eventId, :couponId, :userId, 'PENDING', NOW())")
                    .setParameter("eventId", eventId)
                    .setParameter("couponId", couponId)
                    .setParameter("userId", userId)
                    .executeUpdate();
        });
    }

    @Nested
    class 동시_발급 {

        @Test
        void 수량_100장에_200건_동시_요청하면_발급_수가_100을_초과하지_않는다() throws InterruptedException {
            Long couponId = 1L;
            int maxIssueCount = 100;
            int requestCount = 200;

            insertCoupon(couponId, maxIssueCount);

            for (int i = 0; i < requestCount; i++) {
                insertPendingRequest("evt-" + i, couponId, (long) (i + 1));
            }

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(requestCount);

            for (int i = 0; i < requestCount; i++) {
                int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponIssueProcessor.process("evt-" + index, couponId, (long) (index + 1));
                    } catch (Exception e) {
                        // 무시 — REJECTED 처리됨
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            long issuedCount = issuedCouponJpaRepository.count();
            assertThat(issuedCount).isLessThanOrEqualTo(maxIssueCount);
            assertThat(issuedCount).isEqualTo(maxIssueCount);
        }
    }
}

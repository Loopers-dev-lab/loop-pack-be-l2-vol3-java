package com.loopers.application.coupon;

import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.contract.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class CouponIssueConsumeApplicationServiceConcurrencyTest {

    @Autowired
    private CouponIssueConsumeApplicationService couponIssueConsumeApplicationService;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private ProductMetricsAckPublisher productMetricsAckPublisher;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("선착순 쿠폰 발급은 수량만큼만 성공한다")
    void consume_concurrentRequests_onlyQuantitySucceeds() throws Exception {
        int couponQuantity = 5;
        int requestCount = 20;
        UUID couponId = couponJpaRepository.saveAndFlush(new CouponEntity(
                LocalDateTime.now().plusDays(3),
                couponQuantity,
                couponQuantity
        )).getId();

        List<CouponIssueRequestedEvent> events = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            UUID requestId = UUID.randomUUID();
            couponIssueRequestJpaRepository.saveAndFlush(new CouponIssueRequestEntity(
                    requestId,
                    "coupon-user-" + i,
                    couponId,
                    CouponIssueRequestStatus.PENDING,
                    null,
                    LocalDateTime.now(),
                    null
            ));
            events.add(new CouponIssueRequestedEvent(requestId, couponId, "coupon-user-" + i, LocalDateTime.now()));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);

        for (CouponIssueRequestedEvent event : events) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    couponIssueConsumeApplicationService.consume("coupon-issue-test", event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        executorService.shutdownNow();

        int succeeded = 0;
        int soldOut = 0;
        for (CouponIssueRequestedEvent event : events) {
            CouponIssueRequestStatus status = couponIssueRequestJpaRepository.findByRequestId(event.requestId())
                    .orElseThrow()
                    .getStatus();
            if (status == CouponIssueRequestStatus.SUCCEEDED) {
                succeeded++;
            }
            if (status == CouponIssueRequestStatus.FAILED_SOLD_OUT) {
                soldOut++;
            }
        }

        assertThat(succeeded).isEqualTo(couponQuantity);
        assertThat(soldOut).isEqualTo(requestCount - couponQuantity);
        assertThat(issuedCouponJpaRepository.count()).isEqualTo(couponQuantity);
        assertThat(couponJpaRepository.findById(couponId).orElseThrow().getRemainingQuantity()).isZero();
    }
}

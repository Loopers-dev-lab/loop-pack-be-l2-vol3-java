package com.loopers.application.collector;

import com.loopers.domain.collector.CollectorCouponIssueRequestModel;
import com.loopers.domain.collector.CollectorCouponIssueRequestStatus;
import com.loopers.domain.collector.CollectorCouponModel;
import com.loopers.infrastructure.collector.CollectorCouponIssueRequestJpaRepository;
import com.loopers.infrastructure.collector.CollectorCouponJpaRepository;
import com.loopers.infrastructure.collector.CollectorUserCouponJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(CouponIssueConsumeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CouponIssueConsumeServiceConcurrencyTest {

    @Autowired
    private CouponIssueConsumeService couponIssueConsumeService;

    @Autowired
    private CollectorCouponJpaRepository couponJpaRepository;

    @Autowired
    private CollectorCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private CollectorUserCouponJpaRepository userCouponJpaRepository;

    @AfterEach
    void tearDown() {
        userCouponJpaRepository.deleteAll();
        couponIssueRequestJpaRepository.deleteAll();
        couponJpaRepository.deleteAll();
    }

    @DisplayName("동시 쿠폰 발급 요청 처리 시 수량 제한을 초과하지 않는다")
    @Test
    void doesNotOverIssue_whenConcurrentRequestsExceedIssueLimit() throws InterruptedException {
        // arrange
        long issueLimit = 100L;
        int requestCount = 150;

        CollectorCouponModel coupon = couponJpaRepository.save(
            new CollectorCouponModel(ZonedDateTime.now().plusDays(1), issueLimit)
        );

        List<CollectorCouponIssueRequestModel> requests = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            requests.add(couponIssueRequestJpaRepository.save(new CollectorCouponIssueRequestModel(coupon.getId(), (long) i + 1)));
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(24);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        // act
        for (CollectorCouponIssueRequestModel request : requests) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    couponIssueConsumeService.handleRequest(request.getId(), request.getCouponId(), request.getUserId());
                    CollectorCouponIssueRequestModel updated = couponIssueRequestJpaRepository.findById(request.getId()).orElseThrow();
                    if (updated.getStatus() == CollectorCouponIssueRequestStatus.SUCCEEDED) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // assert
        CollectorCouponModel updatedCoupon = couponJpaRepository.findById(coupon.getId()).orElseThrow();
        long userCouponCount = userCouponJpaRepository.countByCoupon_Id(coupon.getId());
        long successRequestCount = couponIssueRequestJpaRepository.countByCouponIdAndStatus(coupon.getId(), CollectorCouponIssueRequestStatus.SUCCEEDED);
        long failedRequestCount = couponIssueRequestJpaRepository.countByCouponIdAndStatus(coupon.getId(), CollectorCouponIssueRequestStatus.FAILED);

        assertThat(successCount.get()).isEqualTo((int) issueLimit);
        assertThat(failCount.get()).isEqualTo(requestCount - (int) issueLimit);
        assertThat(updatedCoupon.getIssuedCount()).isEqualTo(issueLimit);
        assertThat(userCouponCount).isEqualTo(issueLimit);
        assertThat(successRequestCount).isEqualTo(issueLimit);
        assertThat(failedRequestCount).isEqualTo(requestCount - issueLimit);
    }
}

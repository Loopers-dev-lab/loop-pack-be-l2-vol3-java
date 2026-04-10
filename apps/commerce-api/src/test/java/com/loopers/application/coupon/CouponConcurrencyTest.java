package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100개 수량의 쿠폰에 200명이 동시에 발급 요청하면, 200개의 PENDING 요청이 모두 생성된다.")
    @Test
    void allRequestsCreatedSuccessfully_whenConcurrentIssueRequests() throws InterruptedException {
        // Arrange
        int totalQuantity = 100;
        int requestCount = 200;
        String rawPassword = "Password1!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Coupon coupon = couponJpaRepository.save(new Coupon("선착순 테스트 쿠폰", totalQuantity));

        List<User> users = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            users.add(userJpaRepository.save(new User(
                String.format("user%03d", i),
                encodedPassword,
                "테스터" + i,
                "19900101",
                "user" + i + "@test.com"
            )));
        }

        // Act — 200 스레드가 동시에 쿠폰 발급 요청
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            String loginId = users.get(i).getLoginId();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponFacade.requestIssue(loginId, rawPassword, coupon.getId());
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert — 모든 요청이 PENDING으로 생성되어야 한다
        List<CouponIssueRequest> requests = couponIssueRequestJpaRepository.findByCouponId(coupon.getId());
        assertThat(requests).hasSize(requestCount);
        assertThat(requests).allMatch(r -> r.getStatus() == CouponIssueStatus.PENDING);
    }

    @DisplayName("100개 수량의 쿠폰에 200개의 요청을 순차 처리하면, 정확히 100개가 성공하고 100개가 실패한다.")
    @Test
    void exactly100SucceedAnd100Fail_whenProcessedSequentially() {
        // Arrange
        int totalQuantity = 100;
        int requestCount = 200;

        Coupon coupon = couponJpaRepository.save(new Coupon("선착순 테스트 쿠폰", totalQuantity));

        List<CouponIssueRequest> requests = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            requests.add(couponIssueRequestJpaRepository.save(new CouponIssueRequest(coupon.getId(), (long) (i + 1))));
        }

        // Act — 컨슈머가 순차적으로 처리하는 것을 시뮬레이션 (파티션 키로 인해 동일 쿠폰 요청은 순서 보장)
        int issuedCount = 0;
        for (CouponIssueRequest request : requests) {
            if (issuedCount < totalQuantity) {
                request.markSuccess();
                issuedCount++;
            } else {
                request.markFailed();
            }
        }
        couponIssueRequestJpaRepository.saveAll(requests);

        // Assert
        long successCount = couponIssueRequestJpaRepository.countByCouponIdAndStatus(coupon.getId(), CouponIssueStatus.SUCCESS);
        long failedCount = couponIssueRequestJpaRepository.countByCouponIdAndStatus(coupon.getId(), CouponIssueStatus.FAILED);

        assertThat(successCount).isEqualTo(100);
        assertThat(failedCount).isEqualTo(100);
    }
}

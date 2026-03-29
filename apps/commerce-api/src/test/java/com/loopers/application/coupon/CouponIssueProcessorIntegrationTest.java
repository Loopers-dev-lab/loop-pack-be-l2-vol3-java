package com.loopers.application.coupon;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserDto;
import com.loopers.domain.coupon.*;
import com.loopers.domain.user.Gender;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueProcessorIntegrationTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponIssueFacade couponIssueFacade;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100장 한정 쿠폰에 200명이 동시에 요청하면 정확히 100장만 발급된다")
    @Test
    void concurrentIssue_doesNotExceedMaxQuantity() throws InterruptedException {
        // given: 100장 한정 쿠폰 생성
        int maxQuantity = 100;
        CouponDto.CouponInfo coupon = couponFacade.registerTemplate(
            "선착순 쿠폰", "테스트", CouponType.FIXED,
            BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30), maxQuantity
        );
        Long couponId = coupon.id();

        // given: 200명의 사용자 생성 + 발급 요청 PENDING 저장
        int totalRequests = 200;
        List<Long> requestIds = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            UserDto.UserInfo user = userFacade.register(
                "user" + String.format("%04d", i), "TestPass1!", "테스트유저",
                LocalDate.of(2000, 1, 1), "user" + i + "@test.com", Gender.MALE
            );
            CouponIssueRequest request = CouponIssueRequest.create(couponId, user.id());
            CouponIssueRequest saved = couponIssueRequestRepository.save(request);
            requestIds.add(saved.getId());
        }

        // when: 200개 요청을 동시에 처리 (20 스레드)
        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch done = new CountDownLatch(totalRequests);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Long requestId : requestIds) {
            executor.submit(() -> {
                try {
                    couponIssueProcessor.process(requestId);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then: 에러 출력 및 확인
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("ERROR: " + e.getMessage()));
            errors.getFirst().printStackTrace();
        }
        assertThat(errors).isEmpty();

        // then: 정확히 100장만 발급
        Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(maxQuantity);

        // then: SUCCESS 100건, FAILED 100건
        long successCount = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findById(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.SUCCESS)
            .count();
        long failedCount = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findById(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.FAILED)
            .count();

        assertThat(successCount).isEqualTo(maxQuantity);
        assertThat(failedCount).isEqualTo(totalRequests - maxQuantity);
    }

    @DisplayName("같은 사용자가 여러 번 요청해도 1회만 발급된다")
    @Test
    void concurrentIssue_sameUser_issuesOnlyOnce() throws InterruptedException {
        // given: 무제한 쿠폰 생성
        CouponDto.CouponInfo coupon = couponFacade.registerTemplate(
            "일반 쿠폰", "테스트", CouponType.FIXED,
            BigDecimal.valueOf(3000), ZonedDateTime.now().plusDays(30)
        );
        Long couponId = coupon.id();

        UserDto.UserInfo user = userFacade.register(
            "dupuser01", "TestPass1!", "중복유저",
            LocalDate.of(2000, 1, 1), "dup@test.com", Gender.MALE
        );
        Long userId = user.id();

        // given: 같은 유저가 10번 요청
        int totalRequests = 10;
        List<Long> requestIds = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            CouponIssueRequest request = CouponIssueRequest.create(couponId, userId);
            CouponIssueRequest saved = couponIssueRequestRepository.save(request);
            requestIds.add(saved.getId());
        }

        // when: 동시 처리
        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch done = new CountDownLatch(totalRequests);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Long requestId : requestIds) {
            executor.submit(() -> {
                try {
                    couponIssueProcessor.process(requestId);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then: 발급은 1건만
        long successCount = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findById(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.SUCCESS)
            .count();

        assertThat(successCount).isEqualTo(1);
    }
}

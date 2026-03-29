package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.consumer.CouponIssueRequestProcessor;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Kafka 파티션 직렬화를 시뮬레이션하는 선착순 쿠폰 발급 동시성 테스트.
 *
 * <p>실제 운영 환경에서는 Kafka가 couponId 기준으로 같은 파티션으로 메시지를 라우팅하고,
 * 컨슈머 1개가 순차적으로 처리한다. 이 테스트는 그 순차 처리를 직접 시뮬레이션한다.
 */
@SpringBootTest
class CouponIssueAsyncConcurrencyTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusYears(1);
    private static final int MAX_ISSUABLE = 100;
    private static final int TOTAL_REQUESTS = 500;

    @Autowired
    private CouponIssueRequestProcessor processor;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("선착순 100명 쿠폰에 500명이 순차적으로 발급 요청하면, 정확히 100건만 SUCCESS 처리된다.")
    @Test
    void issuesExactlyMaxIssuable_whenRequestsExceedLimit() {
        // arrange
        Coupon coupon = couponJpaRepository.save(
            new Coupon("선착순 100명 쿠폰", CouponType.FIXED, 1000, 0, FUTURE, MAX_ISSUABLE)
        );

        List<CouponIssueRequest> requests = new ArrayList<>();
        for (long userId = 1; userId <= TOTAL_REQUESTS; userId++) {
            requests.add(couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), userId)
            ));
        }

        // act - Kafka 파티션 직렬화 시뮬레이션: 단일 스레드 순차 처리
        for (CouponIssueRequest request : requests) {
            processor.process(new CouponIssueMessage(
                request.getRequestId(), coupon.getId(), request.getUserId()
            ));
        }

        // assert
        long issuedCount = userCouponJpaRepository.countByCouponId(coupon.getId());
        long successCount = couponIssueRequestJpaRepository.findAll().stream()
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.SUCCESS)
            .count();
        long failedCount = couponIssueRequestJpaRepository.findAll().stream()
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.FAILED)
            .count();

        assertThat(issuedCount).isEqualTo(MAX_ISSUABLE);
        assertThat(successCount).isEqualTo(MAX_ISSUABLE);
        assertThat(failedCount).isEqualTo(TOTAL_REQUESTS - MAX_ISSUABLE);
    }

    @DisplayName("선착순 마감 이후의 요청은 모두 '선착순 마감' 사유로 FAILED 처리된다.")
    @Test
    void marksFailedWithCorrectReason_whenLimitExceeded() {
        // arrange
        Coupon coupon = couponJpaRepository.save(
            new Coupon("선착순 2명 쿠폰", CouponType.FIXED, 500, 0, FUTURE, 2)
        );

        List<CouponIssueRequest> requests = new ArrayList<>();
        for (long userId = 1; userId <= 5; userId++) {
            requests.add(couponIssueRequestJpaRepository.save(
                CouponIssueRequest.create(coupon.getId(), userId)
            ));
        }

        // act
        for (CouponIssueRequest request : requests) {
            processor.process(new CouponIssueMessage(
                request.getRequestId(), coupon.getId(), request.getUserId()
            ));
        }

        // assert
        List<CouponIssueRequest> failedRequests = couponIssueRequestJpaRepository.findAll().stream()
            .filter(r -> r.getStatus() == CouponIssueRequestStatus.FAILED)
            .toList();

        assertThat(failedRequests).hasSize(3);
        assertThat(failedRequests).allMatch(r -> "선착순 마감".equals(r.getFailureReason()));
    }
}
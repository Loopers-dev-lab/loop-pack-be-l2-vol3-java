package com.loopers.domain.coupon;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("선착순 쿠폰 발급 테스트")
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueResultRepository couponIssueResultRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Kafka 파티션 순서 보장: 순차 처리 시 수량 10개 쿠폰에 100명이 요청하면 정확히 10명만 성공한다")
    void issueCoupon_sequentialProcessing_quantityLimit() {
        // Given
        Coupon coupon = couponRepository.save(
                Coupon.create("선착순쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                        null, ZonedDateTime.now().plusDays(30), 10)
        );
        Long couponId = coupon.getId();

        int totalUsers = 100;

        // 각 유저별 CouponIssueResult를 미리 생성 (Facade에서 API 요청 시 생성됨)
        for (int i = 1; i <= totalUsers; i++) {
            couponIssueResultRepository.save(CouponIssueResult.create((long) i, couponId));
        }

        // When — Kafka 파티션 내 순차 처리를 시뮬레이션 (같은 couponId = 같은 파티션 = 단일 스레드)
        for (int i = 1; i <= totalUsers; i++) {
            couponService.issueCouponWithQuantityControl((long) i, couponId);
        }

        // Then
        int successCount = 0;
        int failedCount = 0;
        for (int i = 1; i <= totalUsers; i++) {
            CouponIssueResult result = couponIssueResultRepository
                    .findByUserIdAndCouponId((long) i, couponId).orElseThrow();
            if (result.getStatus() == CouponIssueResultStatus.SUCCESS) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        assertThat(successCount).isEqualTo(10);
        assertThat(failedCount).isEqualTo(90);

        Coupon updatedCoupon = couponRepository.findActiveById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("이미 처리된 결과는 재처리하지 않는다 (멱등성)")
    void issueCoupon_alreadyProcessed_skips() {
        // Given
        Coupon coupon = couponRepository.save(
                Coupon.create("선착순쿠폰", CouponType.FIXED, new BigDecimal("5000"),
                        null, ZonedDateTime.now().plusDays(30), 10)
        );
        Long couponId = coupon.getId();
        Long userId = 1L;

        couponIssueResultRepository.save(CouponIssueResult.create(userId, couponId));

        // When — 같은 요청을 2번 처리 (Kafka 재전달 시뮬레이션)
        couponService.issueCouponWithQuantityControl(userId, couponId);
        couponService.issueCouponWithQuantityControl(userId, couponId); // 2번째는 skip

        // Then — 1번만 발급되어야 함
        CouponIssueResult result = couponIssueResultRepository
                .findByUserIdAndCouponId(userId, couponId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(CouponIssueResultStatus.SUCCESS);

        Coupon updatedCoupon = couponRepository.findActiveById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
    }
}

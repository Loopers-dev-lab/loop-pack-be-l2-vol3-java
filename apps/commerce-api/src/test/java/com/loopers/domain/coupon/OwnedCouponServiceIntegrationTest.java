package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OwnedCouponServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OwnedCouponService ownedCouponService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰을 발급하면, 보유 쿠폰이 DB에 저장된다.")
        @Test
        void savesOwnedCouponToDatabase_whenValidCouponProvided() {
            // arrange
            var coupon = couponService.create("발급 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30));
            var userId = 1L;

            // act
            var result = ownedCouponService.issue(coupon.getId(), userId);

            // assert
            assertAll(
                    () -> assertThat(result.getCoupon().getId()).isEqualTo(coupon.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getStatus()).isEqualTo(OwnedCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(999L, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("삭제된 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsDeleted() {
            // arrange
            var coupon = couponService.create("삭제 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30));
            couponService.delete(coupon.getId());

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("만료된 쿠폰을 발급하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var coupon = couponService.create("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30));
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.EXPIRED_COUPON));
        }

        @DisplayName("이미 발급받은 쿠폰을 중복 발급하면, DUPLICATE_COUPON_ISSUE 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateIssue() {
            // arrange
            var coupon = couponService.create("중복 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30));
            ownedCouponService.issue(coupon.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_COUPON_ISSUED));
        }
    }
}

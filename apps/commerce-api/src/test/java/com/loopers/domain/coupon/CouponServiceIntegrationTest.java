package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class CouponServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @DisplayName("쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정액 쿠폰 정보를 입력하면, 쿠폰이 DB에 저장된다.")
        @Test
        void savesFixedCouponToDatabase_whenValidInputProvided() {
            // arrange
            var expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            var result = couponService.create("정액 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, expiredAt);

            // assert
            var savedCoupon = couponRepository.findById(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedCoupon.getId()).isEqualTo(result.getId()),
                    () -> assertThat(savedCoupon.getName()).isEqualTo(new CouponName("정액 할인 쿠폰")),
                    () -> assertThat(savedCoupon.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(savedCoupon.getDiscountValue()).isEqualTo(5000L),
                    () -> assertThat(savedCoupon.getMaxDiscountPrice()).isNull(),
                    () -> assertThat(savedCoupon.getMinOrderPrice()).isEqualTo(Money.wons(10000L))
            );
        }

        @DisplayName("정률 쿠폰 정보를 입력하면, 쿠폰이 DB에 저장된다.")
        @Test
        void savesRateCouponToDatabase_whenValidInputProvided() {
            // arrange
            var expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            var result = couponService.create("정률 할인 쿠폰", CouponType.RATE, 10L, 5000L, 20000L, expiredAt);

            // assert
            var savedCoupon = couponRepository.findById(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedCoupon.getId()).isEqualTo(result.getId()),
                    () -> assertThat(savedCoupon.getName()).isEqualTo(new CouponName("정률 할인 쿠폰")),
                    () -> assertThat(savedCoupon.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(savedCoupon.getDiscountValue()).isEqualTo(10L),
                    () -> assertThat(savedCoupon.getMaxDiscountPrice()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(savedCoupon.getMinOrderPrice()).isEqualTo(Money.wons(20000L))
            );
        }

        @DisplayName("만료일이 과거이면, INVALID_EXPIRED_AT 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            // arrange
            var pastExpiredAt = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> couponService.create("쿠폰", CouponType.FIXED, 5000L, null, 10000L, pastExpiredAt))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }
}

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
            var result = couponService.create(new CouponTerms("정액 할인 쿠폰", CouponType.FIXED, 5000L, null, 10000L, expiredAt, 10000));

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
            var result = couponService.create(new CouponTerms("정률 할인 쿠폰", CouponType.RATE, 10L, 5000L, 20000L, expiredAt, 10000));

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
            assertThatThrownBy(() -> couponService.create(new CouponTerms("쿠폰", CouponType.FIXED, 5000L, null, 10000L, pastExpiredAt, 10000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_EXPIRED_AT));
        }
    }

    @DisplayName("쿠폰을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 정보를 입력하면, 쿠폰 정보가 DB에 반영된다.")
        @Test
        void updatesCouponInDatabase_whenValidInputProvided() {
            // arrange
            var expiredAt = ZonedDateTime.now().plusDays(30);
            var coupon = couponService.create(new CouponTerms("기존 쿠폰", CouponType.FIXED, 5000L, null, 10000L, expiredAt, 10000));
            var newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            couponService.update(new ModifyCoupon(coupon.getId(),"수정된 쿠폰", 3000L, null, 20000L, newExpiredAt));

            // assert
            var updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updatedCoupon.getName()).isEqualTo(new CouponName("수정된 쿠폰")),
                    () -> assertThat(updatedCoupon.getDiscountValue()).isEqualTo(3000L),
                    () -> assertThat(updatedCoupon.getMinOrderPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(updatedCoupon.getExpiredAt()).isEqualTo(newExpiredAt)
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 수정하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act & assert
            assertThatThrownBy(() -> couponService.update(new ModifyCoupon(999L,"쿠폰", 5000L, null, 10000L, ZonedDateTime.now().plusDays(30))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }
    }

    @DisplayName("쿠폰을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 쿠폰을 삭제하면, deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenCouponExists() {
            // arrange
            var coupon = couponService.create(new CouponTerms("삭제 대상 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));

            // act
            var result = couponService.delete(coupon.getId());

            // assert
            var deletedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(deletedCoupon.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 삭제하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act & assert
            assertThatThrownBy(() -> couponService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("이미 삭제된 쿠폰을 삭제하면, false를 반환한다.")
        @Test
        void returnsFalse_whenCouponAlreadyDeleted() {
            // arrange
            var coupon = couponService.create(new CouponTerms("삭제 대상 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));
            couponService.delete(coupon.getId());

            // act
            var result = couponService.delete(coupon.getId());

            // assert
            assertThat(result).isFalse();
        }
    }
}

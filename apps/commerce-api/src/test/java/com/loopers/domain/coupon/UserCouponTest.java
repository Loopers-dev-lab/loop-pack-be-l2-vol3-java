package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    @DisplayName("UserCoupon 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 쿠폰과 사용자 ID 로 생성하면, UserCoupon 이 생성된다.")
        @Test
        void createsUserCoupon_whenValidCouponAndUserId() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            Long userId = 1L;

            // act
            UserCoupon result = UserCoupon.of(coupon, userId);

            // assert
            assertThat(result).isNotNull();
        }

        @DisplayName("쿠폰이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenCouponIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                UserCoupon.of(null, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("쿠폰은 필수입니다.");
        }

        @DisplayName("사용자 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                UserCoupon.of(coupon, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("사용자ID는 필수입니다.");
        }

        @DisplayName("만료된 쿠폰으로 생성하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenCouponIsExpired() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().minusDays(1));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                UserCoupon.of(coupon, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("만료된 쿠폰입니다.");
        }
    }

    @DisplayName("calculateDiscount() 를 호출할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입 쿠폰은 주문 금액과 관계없이 고정 금액을 반환한다.")
        @Test
        void returnsFixedValue_whenCouponTypeIsFixed() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            long result = userCoupon.calculateDiscount(10000L);

            // assert
            assertThat(result).isEqualTo(3000L);
        }

        @DisplayName("RATE 타입 쿠폰은 주문 금액의 비율만큼 할인 금액을 반환한다.")
        @Test
        void returnsRateBasedDiscount_whenCouponTypeIsRate() {
            // arrange
            Coupon coupon = Coupon.of("10% 할인 쿠폰", "RATE", 10, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            long result = userCoupon.calculateDiscount(10000L);

            // assert
            assertThat(result).isEqualTo(1000L);
        }

        @DisplayName("RATE 타입 쿠폰은 나눗셈 결과에서 소수점을 버린 금액을 반환한다.")
        @Test
        void truncatesDecimal_whenRateDiscountResultIsNotInteger() {
            // arrange
            Coupon coupon = Coupon.of("10% 할인 쿠폰", "RATE", 10, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            long result = userCoupon.calculateDiscount(1005L);  // 1005 * 10 / 100 = 100

            // assert
            assertThat(result).isEqualTo(100L);
        }
    }

    @DisplayName("use() 를 호출할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰은 정상적으로 사용된다.")
        @Test
        void usesSuccessfully_whenStatusIsAvailable() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            userCoupon.use(1L);

            // assert
            assertThat(userCoupon.status()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("본인 소유가 아닌 쿠폰을 사용하면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenNotOwner() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.use(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(result.getCustomMessage()).isEqualTo("본인의 쿠폰만 사용할 수 있습니다.");
        }

        @DisplayName("이미 사용된 쿠폰을 사용하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsUsed() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);
            userCoupon.use(1L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.use(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("이미 사용된 쿠폰입니다.");
        }

        @DisplayName("만료된 쿠폰을 사용하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsExpired() throws Exception {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            java.lang.reflect.Field field = UserCoupon.class.getDeclaredField("expiredAt");
            field.setAccessible(true);
            field.set(userCoupon, ZonedDateTime.now().minusDays(1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.use(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("만료된 쿠폰입니다.");
        }
    }

    @DisplayName("validate() 를 호출할 때, ")
    @Nested
    class Validate {

        @DisplayName("본인 소유의 사용 가능한 쿠폰이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenValidCoupon() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act & assert
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> userCoupon.validate(1L));
        }

        @DisplayName("본인 소유가 아닌 쿠폰이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenNotOwner() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.validate(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(result.getCustomMessage()).isEqualTo("본인의 쿠폰만 사용할 수 있습니다.");
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsUsed() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);
            userCoupon.use(1L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.validate(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("이미 사용된 쿠폰입니다.");
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStatusIsExpired() throws Exception {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            java.lang.reflect.Field field = UserCoupon.class.getDeclaredField("expiredAt");
            field.setAccessible(true);
            field.set(userCoupon, ZonedDateTime.now().minusDays(1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> userCoupon.validate(1L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("만료된 쿠폰입니다.");
        }
    }

    @DisplayName("status() 를 호출할 때, ")
    @Nested
    class Status {

        @DisplayName("만료일이 지나지 않은 쿠폰은 AVAILABLE 을 반환한다.")
        @Test
        void returnsAvailable_whenCouponIsNotExpired() {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            // act
            CouponStatus result = userCoupon.status();

            // assert
            assertThat(result).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("만료일이 지난 쿠폰은 EXPIRED 를 반환한다.")
        @Test
        void returnsExpired_whenCouponIsExpired() throws Exception {
            // arrange
            Coupon coupon = Coupon.of("3000원 할인 쿠폰", "FIXED", 3000, ZonedDateTime.now().plusDays(30));
            UserCoupon userCoupon = UserCoupon.of(coupon, 1L);

            java.lang.reflect.Field field = UserCoupon.class.getDeclaredField("expiredAt");
            field.setAccessible(true);
            field.set(userCoupon, ZonedDateTime.now().minusDays(1));

            // act
            CouponStatus result = userCoupon.status();

            // assert
            assertThat(result).isEqualTo(CouponStatus.EXPIRED);
        }
    }
}

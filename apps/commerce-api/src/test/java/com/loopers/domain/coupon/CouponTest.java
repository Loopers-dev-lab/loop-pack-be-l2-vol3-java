package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    @DisplayName("Coupon 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정액 할인 쿠폰을 정상적인 값으로 생성하면, Coupon 이 생성된다.")
        @Test
        void createsCoupon_whenFixedTypeWithValidValue() {
            // arrange
            String name = "3000원 할인 쿠폰";
            int value = 3000;
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            Coupon result = Coupon.of(name, "FIXED", value, expiredAt);

            // assert
            assertThat(result).isNotNull();
        }

        @DisplayName("정률 할인 쿠폰을 정상적인 값으로 생성하면, Coupon 이 생성된다.")
        @Test
        void createsCoupon_whenRateTypeWithValidValue() {
            // arrange
            String name = "10% 할인 쿠폰";
            int value = 10;
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            Coupon result = Coupon.of(name, "RATE", value, expiredAt);

            // assert
            assertThat(result).isNotNull();
        }

        @DisplayName("유효하지 않은 쿠폰 타입 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTypeIsInvalid() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "INVALID", 1000, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("유효하지 않은 쿠폰 타입입니다.");
        }

        @DisplayName("쿠폰 타입이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTypeIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", null, 1000, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("유효하지 않은 쿠폰 타입입니다.");
        }

        @DisplayName("할인 값이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsZero() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "FIXED", 0, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("할인 값은 0보다 커야 합니다.");
        }

        @DisplayName("할인 값이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNegative() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "FIXED", -1, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("할인 값은 0보다 커야 합니다.");
        }

        @DisplayName("정률 할인 쿠폰의 할인율이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenRateValueExceeds100() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "RATE", 101, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("정률 할인일 때 할인율이 100퍼센트를 초과할 수 없습니다.");
        }

        @DisplayName("정률 할인 쿠폰의 할인율이 100이면, Coupon 이 생성된다.")
        @Test
        void createsCoupon_whenRateValueIs100() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            Coupon result = Coupon.of("100% 할인 쿠폰", "RATE", 100, expiredAt);

            // assert
            assertThat(result).isNotNull();
        }

        @DisplayName("발급 수량을 지정하면, 수량 제한 쿠폰이 생성된다.")
        @Test
        void createsCoupon_whenTotalQuantityIsProvided() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            Coupon result = Coupon.of("선착순 쿠폰", "FIXED", 1000, 100, expiredAt);

            // assert
            assertThat(result.isLimited()).isTrue();
            assertThat(result.totalQuantity()).isEqualTo(100);
        }

        @DisplayName("발급 수량 없이 생성하면, 수량 제한 없는 쿠폰이 생성된다.")
        @Test
        void createsCoupon_whenTotalQuantityIsNotProvided() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            Coupon result = Coupon.of("일반 쿠폰", "FIXED", 1000, expiredAt);

            // assert
            assertThat(result.isLimited()).isFalse();
        }

        @DisplayName("발급 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTotalQuantityIsZero() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "FIXED", 1000, 0, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("발급 수량은 0보다 커야 합니다.");
        }

        @DisplayName("발급 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTotalQuantityIsNegative() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Coupon.of("쿠폰", "FIXED", 1000, -1, ZonedDateTime.now().plusDays(30))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("발급 수량은 0보다 커야 합니다.");
        }
    }

    @DisplayName("Coupon 을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정하면, name, value, expiredAt 이 변경된다.")
        @Test
        void updatesCoupon_whenValidValues() {
            // arrange
            Coupon coupon = Coupon.of("기존 쿠폰", "FIXED", 1000, ZonedDateTime.now().plusDays(10));
            String newName = "수정된 쿠폰";
            int newValue = 2000;
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update(newName, newValue, newExpiredAt);

            // assert
            assertThat(coupon.name()).isEqualTo(newName);
            assertThat(coupon.value()).isEqualTo(newValue);
            assertThat(coupon.expiredAt()).isEqualTo(newExpiredAt);
        }

        @DisplayName("수정할 할인 값이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsZero() {
            // arrange
            Coupon coupon = Coupon.of("기존 쿠폰", "FIXED", 1000, ZonedDateTime.now().plusDays(10));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                coupon.update("수정된 쿠폰", 0, ZonedDateTime.now().plusDays(60))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("할인 값은 0보다 커야 합니다.");
        }

        @DisplayName("수정할 할인 값이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNegative() {
            // arrange
            Coupon coupon = Coupon.of("기존 쿠폰", "FIXED", 1000, ZonedDateTime.now().plusDays(10));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                coupon.update("수정된 쿠폰", -1, ZonedDateTime.now().plusDays(60))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("할인 값은 0보다 커야 합니다.");
        }

        @DisplayName("정률 할인 쿠폰의 수정할 할인율이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenRateValueExceeds100() {
            // arrange
            Coupon coupon = Coupon.of("기존 쿠폰", "RATE", 10, ZonedDateTime.now().plusDays(10));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                coupon.update("수정된 쿠폰", 101, ZonedDateTime.now().plusDays(60))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getCustomMessage()).isEqualTo("정률 할인일 때 할인율이 100퍼센트를 초과할 수 없습니다.");
        }
    }
}

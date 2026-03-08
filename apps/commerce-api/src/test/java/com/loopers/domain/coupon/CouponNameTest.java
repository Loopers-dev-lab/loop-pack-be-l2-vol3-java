package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class CouponNameTest {

    @DisplayName("쿠폰명을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면, 정상적으로 생성된다.")
        @Test
        void createsCouponName_whenNameIsValid() {
            // arrange
            var name = "여름 할인 쿠폰";

            // act
            var couponName = new CouponName(name);

            // assert
            assertThat(couponName.getValue()).isEqualTo(name);
        }

        @DisplayName("이름 길이가 경계값이면, 정상적으로 생성된다.")
        @ParameterizedTest(name = "길이가 {0}인 이름")
        @ValueSource(ints = {2, 50})
        void createsCouponName_whenNameLengthIsAtBoundary(int length) {
            // arrange
            var name = "a".repeat(length);

            // act & assert
            assertThatCode(() -> new CouponName(name))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이름이 null이면, REQUIRED_COUPON_NAME 예외가 발생한다.")
        @Test
        void throwsRequiredCouponNameException_whenNameIsNull() {
            assertThatThrownBy(() -> new CouponName(null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_COUPON_NAME));
        }

        @DisplayName("공백만으로 이루어진 이름이면, REQUIRED_COUPON_NAME 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsBlank() {
            assertThatThrownBy(() -> new CouponName("   "))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.REQUIRED_COUPON_NAME));
        }

        @DisplayName("이름 길이가 유효하지 않으면, INVALID_COUPON_NAME 예외가 발생한다.")
        @ParameterizedTest(name = "길이가 {0}인 이름")
        @ValueSource(ints = {1, 51})
        void throwsInvalidCouponNameException_whenNameLengthIsInvalid(int length) {
            // arrange
            var name = "a".repeat(length);

            // act & assert
            assertThatThrownBy(() -> new CouponName(name))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_NAME));
        }
    }
}

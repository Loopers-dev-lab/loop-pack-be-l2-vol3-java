package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private static final String VALID_NAME = "신규가입 10% 할인";
    private static final int VALID_VALUE = 10;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);
    private static final LocalDateTime PAST_EXPIRED_AT = LocalDateTime.now().minusDays(1);

    private CouponTemplate validFixedTemplate(int value) {
        return new CouponTemplate(VALID_NAME, CouponType.FIXED, value, null, FUTURE_EXPIRED_AT);
    }

    private CouponTemplate validRateTemplate(int value) {
        return new CouponTemplate(VALID_NAME, CouponType.RATE, value, null, FUTURE_EXPIRED_AT);
    }

    @DisplayName("CouponTemplate 생성 시")
    @Nested
    class Create {

        @DisplayName("정상적인 파라미터로 쿠폰 템플릿이 생성된다.")
        @Test
        void createsCouponTemplate_whenValidParameters() {
            // act
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, 10000, FUTURE_EXPIRED_AT);

            // assert
            assertThat(template.getName()).isEqualTo(VALID_NAME);
            assertThat(template.getType()).isEqualTo(CouponType.RATE);
            assertThat(template.getValue()).isEqualTo(VALID_VALUE);
            assertThat(template.getMinOrderAmount()).isEqualTo(10000);
        }

        @DisplayName("name이 blank이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplate("  ", CouponType.RATE, VALID_VALUE, null, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("value가 0 이하이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplate(VALID_NAME, CouponType.FIXED, 0, null, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입에서 value가 100을 초과하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceedsHundred() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplate(VALID_NAME, CouponType.RATE, 101, null, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입에서 value가 100이면 정상 생성된다.")
        @Test
        void createsCouponTemplate_whenRateValueIsHundred() {
            // act
            CouponTemplate template = new CouponTemplate(VALID_NAME, CouponType.RATE, 100, null, FUTURE_EXPIRED_AT);

            // assert
            assertThat(template.getValue()).isEqualTo(100);
        }

        @DisplayName("minOrderAmount가 음수이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplate(VALID_NAME, CouponType.FIXED, VALID_VALUE, -1, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("CouponTemplate 수정 시")
    @Nested
    class Update {

        @DisplayName("RATE 타입에서 value가 100을 초과하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceedsHundred() {
            // arrange
            CouponTemplate template = new CouponTemplate(VALID_NAME, CouponType.RATE, VALID_VALUE, null, FUTURE_EXPIRED_AT);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update(VALID_NAME, CouponType.RATE, 101, null, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 음수이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            // arrange
            CouponTemplate template = new CouponTemplate(VALID_NAME, CouponType.FIXED, VALID_VALUE, null, FUTURE_EXPIRED_AT);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update(VALID_NAME, CouponType.FIXED, VALID_VALUE, -1, FUTURE_EXPIRED_AT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("calculateDiscount() 시")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입은 value만큼 할인한다.")
        @Test
        void returnsFixedValue_whenTypeIsFixed() {
            // arrange
            CouponTemplate template = validFixedTemplate(3000);

            // act
            int discount = template.calculateDiscount(10000);

            // assert
            assertThat(discount).isEqualTo(3000);
        }

        @DisplayName("FIXED 타입에서 value가 주문금액보다 크면 주문금액만큼만 할인한다.")
        @Test
        void returnsOrderAmount_whenFixedValueExceedsOrderAmount() {
            // arrange
            CouponTemplate template = validFixedTemplate(20000);

            // act
            int discount = template.calculateDiscount(10000);

            // assert
            assertThat(discount).isEqualTo(10000);
        }

        @DisplayName("RATE 타입은 주문금액의 value%만큼 할인한다.")
        @Test
        void returnsRateValue_whenTypeIsRate() {
            // arrange
            CouponTemplate template = validRateTemplate(10);

            // act
            int discount = template.calculateDiscount(10000);

            // assert
            assertThat(discount).isEqualTo(1000);
        }
    }

    @DisplayName("isSatisfyMinOrderAmount() 시")
    @Nested
    class IsSatisfyMinOrderAmount {

        @DisplayName("minOrderAmount가 null이면 항상 true를 반환한다.")
        @Test
        void returnsTrue_whenMinOrderAmountIsNull() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, null, FUTURE_EXPIRED_AT);

            // act & assert
            assertThat(template.isSatisfyMinOrderAmount(0)).isTrue();
        }

        @DisplayName("주문금액이 minOrderAmount 이상이면 true를 반환한다.")
        @Test
        void returnsTrue_whenOrderAmountIsAboveMinimum() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, 5000, FUTURE_EXPIRED_AT);

            // act & assert
            assertThat(template.isSatisfyMinOrderAmount(5000)).isTrue();
        }

        @DisplayName("주문금액이 minOrderAmount 미만이면 false를 반환한다.")
        @Test
        void returnsFalse_whenOrderAmountIsBelowMinimum() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, 5000, FUTURE_EXPIRED_AT);

            // act & assert
            assertThat(template.isSatisfyMinOrderAmount(4999)).isFalse();
        }
    }

    @DisplayName("isExpired() 시")
    @Nested
    class IsExpired {

        @DisplayName("현재 시각이 expiredAt 이후면 true를 반환한다.")
        @Test
        void returnsTrue_whenExpiredAtIsInThePast() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, null, PAST_EXPIRED_AT);

            // act & assert
            assertThat(template.isExpired(LocalDateTime.now())).isTrue();
        }

        @DisplayName("현재 시각이 expiredAt 이전이면 false를 반환한다.")
        @Test
        void returnsFalse_whenExpiredAtIsInTheFuture() {
            // arrange
            CouponTemplate template = new CouponTemplate(
                    VALID_NAME, CouponType.RATE, VALID_VALUE, null, FUTURE_EXPIRED_AT);

            // act & assert
            assertThat(template.isExpired(LocalDateTime.now())).isFalse();
        }
    }
}

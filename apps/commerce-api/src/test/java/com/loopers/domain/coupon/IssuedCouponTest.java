package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IssuedCouponTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    private IssuedCoupon createIssuedCoupon(CouponType type, int value, BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        return IssuedCoupon.create(1L, 100L, "테스트 쿠폰", type, value, minOrderAmount, expiredAt);
    }

    @Nested
    class 사용_가능_검증 {

        @Test
        void 정상_상태이면_검증을_통과한다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000, null, FUTURE);

            assertThatCode(issuedCoupon::validateUsable)
                    .doesNotThrowAnyException();
        }

        @Test
        void 삭제된_쿠폰이면_예외() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000, null, FUTURE);
            issuedCoupon.delete();

            assertThatThrownBy(issuedCoupon::validateUsable)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        void 사용된_쿠폰이면_예외() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000, null, FUTURE);
            issuedCoupon.use();

            assertThatThrownBy(issuedCoupon::validateUsable)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("사용할 수 없는 쿠폰입니다");
        }

        @Test
        void 만료된_쿠폰이면_예외() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000, null, FUTURE);
            ReflectionTestUtils.setField(issuedCoupon, "expiredAt", LocalDateTime.now().minusDays(1));

            assertThatThrownBy(issuedCoupon::validateUsable)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("사용할 수 없는 쿠폰입니다");
        }
    }

    @Nested
    class 할인_계산 {

        @Test
        void 정액_할인값이_총액보다_작으면_할인값을_반환한다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 5000, null, FUTURE);

            BigDecimal discount = issuedCoupon.calculateDiscount(BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        void 정액_할인값이_총액보다_크면_총액을_반환한다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 5000, null, FUTURE);

            BigDecimal discount = issuedCoupon.calculateDiscount(BigDecimal.valueOf(3000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        void 정률_할인은_소수점_이하를_버린다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.RATE, 33, null, FUTURE);

            BigDecimal discount = issuedCoupon.calculateDiscount(BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3300));
        }
    }

    @Nested
    class 최소_주문_금액_검증 {

        @Test
        void 총액이_최소_주문_금액_미만이면_예외() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), FUTURE);

            assertThatThrownBy(() -> issuedCoupon.validateMinOrderAmount(BigDecimal.valueOf(5000)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("최소 주문 금액 조건을 충족하지 않습니다");
        }

        @Test
        void 총액이_최소_주문_금액_이상이면_통과한다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), FUTURE);

            assertThatCode(() -> issuedCoupon.validateMinOrderAmount(BigDecimal.valueOf(10000)))
                    .doesNotThrowAnyException();
        }

        @Test
        void 최소_주문_금액이_null이면_통과한다() {
            IssuedCoupon issuedCoupon = createIssuedCoupon(CouponType.FIXED, 1000, null, FUTURE);

            assertThatCode(() -> issuedCoupon.validateMinOrderAmount(BigDecimal.valueOf(100)))
                    .doesNotThrowAnyException();
        }
    }
}

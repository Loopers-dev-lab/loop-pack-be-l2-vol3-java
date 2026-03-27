package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CouponTest {

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("수량이 남아있으면, issuedCount가 1 증가한다.")
        @Test
        void incrementsIssuedCount_whenStockRemains() {
            // arrange
            Coupon coupon = createCoupon(10, 5);

            // act
            coupon.issue();

            // assert
            assertThat(coupon.getIssuedCount()).isEqualTo(6);
        }

        @DisplayName("수량이 소진되면, 예외가 발생한다.")
        @Test
        void throwsException_whenSoldOut() {
            // arrange
            Coupon coupon = createCoupon(10, 10);

            // act & assert
            assertThatThrownBy(coupon::issue)
                    .isInstanceOf(IllegalStateException.class);
        }

        @DisplayName("여러 번 호출하면 누적된다.")
        @Test
        void accumulates() {
            // arrange
            Coupon coupon = createCoupon(10, 0);

            // act
            coupon.issue();
            coupon.issue();
            coupon.issue();

            // assert
            assertThat(coupon.getIssuedCount()).isEqualTo(3);
        }
    }

    private Coupon createCoupon(int totalQuantity, int issuedCount) {
        try {
            Coupon coupon = Coupon.class.getDeclaredConstructor().newInstance();
            setField(coupon, "id", 1L);
            setField(coupon, "totalQuantity", totalQuantity);
            setField(coupon, "issuedCount", issuedCount);
            return coupon;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

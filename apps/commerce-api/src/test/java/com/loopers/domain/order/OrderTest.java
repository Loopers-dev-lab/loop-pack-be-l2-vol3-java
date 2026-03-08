package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_주문이_생성된다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 2);
            order.addItem(2L, "셔츠", new BigDecimal("30000"), 1);

            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getOrderItems()).hasSize(2);
        }

        @Test
        void 사용자ID가_null이면_예외() {
            assertThatThrownBy(() -> Order.create(null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("사용자 ID는 필수입니다");
        }

        @Test
        void 주문상품이_100개를_초과하면_예외() {
            Order order = Order.create(1L);
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> order.addItem((long) i, "상품" + i, new BigDecimal("1000"), 1));

            assertThatThrownBy(() -> order.addItem(101L, "상품101", new BigDecimal("1000"), 1))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("주문 상품은 100개 이하여야 합니다");
        }

        @Test
        void 동일_상품ID가_중복되면_예외() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 2);

            assertThatThrownBy(() -> order.addItem(1L, "운동화", new BigDecimal("50000"), 3))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("주문 상품이 중복되었습니다");
        }
    }

    @Nested
    class 소유권_확인 {

        @Test
        void 본인의_주문이면_true를_반환한다() {
            Order order = Order.create(1L);

            assertThat(order.isOwnedBy(1L)).isTrue();
        }

        @Test
        void 본인의_주문이_아니면_false를_반환한다() {
            Order order = Order.create(1L);

            assertThat(order.isOwnedBy(2L)).isFalse();
        }
    }

    @Nested
    class 쿠폰_적용 {

        @Test
        void 쿠폰_미적용_시_discountAmount는_0이고_finalAmount는_totalAmount와_동일하다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 2);

            assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getFinalAmount()).isEqualByComparingTo(new BigDecimal("100000"));
            assertThat(order.getIssuedCouponId()).isNull();
        }

        @Test
        void 정액_쿠폰_적용_시_discountAmount만큼_차감된_finalAmount를_반환한다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 2);

            order.applyCoupon(10L, new BigDecimal("5000"));

            assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("5000"));
            assertThat(order.getFinalAmount()).isEqualByComparingTo(new BigDecimal("95000"));
            assertThat(order.getIssuedCouponId()).isEqualTo(10L);
        }

        @Test
        void 할인_금액이_totalAmount를_초과해도_finalAmount는_0_이상이다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("1000"), 1);

            order.applyCoupon(10L, new BigDecimal("5000"));

            assertThat(order.getFinalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class 총_주문금액_계산 {

        @Test
        void 총_주문금액은_각_주문상품의_주문금액_합계이다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 2);   // 100,000
            order.addItem(2L, "셔츠", new BigDecimal("30000"), 1);    // 30,000

            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("130000"));
        }

        @Test
        void 상품이_하나면_해당_상품의_주문금액이_총_주문금액이다() {
            Order order = Order.create(1L);
            order.addItem(1L, "운동화", new BigDecimal("50000"), 3);    // 150,000

            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        }
    }
}

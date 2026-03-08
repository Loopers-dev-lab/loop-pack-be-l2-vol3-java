package com.loopers.domain.order;

import com.loopers.domain.Quantity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("Order를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, Order가 생성된다.")
        @Test
        void createsOrder_whenValidInfo() {
            Order order = new Order(1L, new Money(50000));

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(50000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(null, new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalPrice가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(1L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 항목을 추가할 때, ")
    @Nested
    class AddItems {

        @DisplayName("올바른 항목이면, 주문 항목이 추가된다.")
        @Test
        void addsItems_whenValidCommands() {
            Order order = new Order(1L, new Money(50000));
            List<OrderItemCommand> commands = List.of(
                new OrderItemCommand(1L, "에어맥스", new Money(25000), "나이키", 2)
            );

            order.addItems(commands);

            assertAll(
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(order.getItems().get(0).getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(order.getItems().get(0).getQuantity()).isEqualTo(new Quantity(2))
            );
        }
    }

    @DisplayName("쿠폰이 적용된 Order를 생성할 때, ")
    @Nested
    class CreateWithCoupon {

        @DisplayName("올바른 정보이면, 할인이 적용된 Order가 생성된다.")
        @Test
        void createsOrder_withCouponDiscount() {
            Order order = new Order(1L, new Money(50000), new Money(5000), 10L);

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalPrice()).isEqualTo(new Money(50000)),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(new Money(5000)),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(45000)),
                () -> assertThat(order.getCouponIssueId()).isEqualTo(10L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED)
            );
        }

        @DisplayName("쿠폰 없이 생성하면, originalPrice와 totalPrice가 동일하다.")
        @Test
        void createsOrder_withoutCoupon() {
            Order order = new Order(1L, new Money(50000));

            assertAll(
                () -> assertThat(order.getOriginalPrice()).isEqualTo(new Money(50000)),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(new Money(0)),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(50000)),
                () -> assertThat(order.getCouponIssueId()).isNull()
            );
        }
    }

    @DisplayName("주문을 취소할 때, ")
    @Nested
    class Cancel {

        @DisplayName("ORDERED 상태이면, 취소된다.")
        @Test
        void cancelsOrder_whenStatusIsOrdered() {
            Order order = new Order(1L, new Money(50000));

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 취소된 주문이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCancelled() {
            Order order = new Order(1L, new Money(50000));
            order.cancel();

            CoreException result = assertThrows(CoreException.class, order::cancel);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

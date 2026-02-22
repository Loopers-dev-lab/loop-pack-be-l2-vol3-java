package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 도메인 테스트")
class OrderTest {

    private List<OrderItem> sampleOrderItems;

    @BeforeEach
    void setUp() {
        sampleOrderItems = List.of(
                OrderItem.of(1L, "상품A", Money.of(10000L), 2),
                OrderItem.of(2L, "상품B", Money.of(5000L), 1)
        );
    }

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("주문을 생성하면 PENDING 상태가 된다")
        void createOrderWithPendingStatus() {
            Order order = Order.create(1L, sampleOrderItems);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getOrderItems()).hasSize(2);
        }

        @Test
        @DisplayName("총 주문 금액을 계산할 수 있다")
        void calculateTotalAmount() {
            Order order = Order.create(1L, sampleOrderItems);

            Money totalAmount = order.getTotalAmount();

            assertThat(totalAmount.getAmount()).isEqualByComparingTo("25000");
        }

        @Test
        @DisplayName("주문 항목이 비어있으면 예외가 발생한다")
        void createWithEmptyItemsThrowsException() {
            assertThatThrownBy(() -> Order.create(1L, List.of()))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("주문 항목은 최소 1개 이상");
        }

        @Test
        @DisplayName("사용자 ID가 없으면 예외가 발생한다")
        void createWithNullUserIdThrowsException() {
            assertThatThrownBy(() -> Order.create(null, sampleOrderItems))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("결제 상태 전이 테스트")
    class PayTest {

        @Test
        @DisplayName("PENDING 상태에서 결제하면 PAID 상태가 된다")
        void payFromPending() {
            Order order = Order.create(1L, sampleOrderItems);

            order.pay();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 결제하면 예외가 발생한다")
        void payFromNonPendingThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();

            assertThatThrownBy(order::pay)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 대기 상태에서만");
        }
    }

    @Nested
    @DisplayName("준비 상태 전이 테스트")
    class PrepareTest {

        @Test
        @DisplayName("PAID 상태에서 준비하면 PREPARING 상태가 된다")
        void prepareFromPaid() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();

            order.prepare();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("PAID가 아닌 상태에서 준비하면 예외가 발생한다")
        void prepareFromNonPaidThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);

            assertThatThrownBy(order::prepare)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 완료 상태에서만");
        }
    }

    @Nested
    @DisplayName("배송 상태 전이 테스트")
    class ShipTest {

        @Test
        @DisplayName("PREPARING 상태에서 배송하면 SHIPPED 상태가 된다")
        void shipFromPreparing() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();

            order.ship();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("PREPARING이 아닌 상태에서 배송하면 예외가 발생한다")
        void shipFromNonPreparingThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();

            assertThatThrownBy(order::ship)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("준비 완료 상태에서만");
        }
    }

    @Nested
    @DisplayName("배송 완료 상태 전이 테스트")
    class DeliverTest {

        @Test
        @DisplayName("SHIPPED 상태에서 배송 완료하면 DELIVERED 상태가 된다")
        void deliverFromShipped() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();
            order.ship();

            order.deliver();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("SHIPPED가 아닌 상태에서 배송 완료하면 예외가 발생한다")
        void deliverFromNonShippedThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();

            assertThatThrownBy(order::deliver)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("배송 중 상태에서만");
        }
    }

    @Nested
    @DisplayName("취소 상태 전이 테스트")
    class CancelTest {

        @Test
        @DisplayName("PENDING 상태에서 취소할 수 있다")
        void cancelFromPending() {
            Order order = Order.create(1L, sampleOrderItems);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("PAID 상태에서 취소할 수 있다")
        void cancelFromPaid() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("PREPARING 상태에서는 취소할 수 없다")
        void cancelFromPreparingThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("취소할 수 없는 주문 상태");
        }

        @Test
        @DisplayName("SHIPPED 상태에서는 취소할 수 없다")
        void cancelFromShippedThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();
            order.ship();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("취소할 수 없는 주문 상태");
        }

        @Test
        @DisplayName("DELIVERED 상태에서는 취소할 수 없다")
        void cancelFromDeliveredThrowsException() {
            Order order = Order.create(1L, sampleOrderItems);
            order.pay();
            order.prepare();
            order.ship();
            order.deliver();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("취소할 수 없는 주문 상태");
        }
    }
}

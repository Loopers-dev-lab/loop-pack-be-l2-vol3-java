package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.order.InMemoryOrderItemRepository;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class OrderCompensationServiceTest {

    private InMemoryOrderRepository orderRepository;
    private InMemoryOrderItemRepository orderItemRepository;
    private OrderService orderService;
    private IssuedCouponService issuedCouponService;
    private ProductService productService;
    private OrderCompensationService orderCompensationService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderItemRepository = new InMemoryOrderItemRepository();
        orderService = new OrderService(orderRepository, orderItemRepository);
        issuedCouponService = mock(IssuedCouponService.class);
        productService = mock(ProductService.class);
        orderCompensationService = new OrderCompensationService(orderService, issuedCouponService, productService);
    }

    @DisplayName("결제 실패로 인한 주문 보상 처리 시, ")
    @Nested
    class Compensate {

        @DisplayName("주문이 실패 상태로 전환된다.")
        @Test
        void marksOrderAsFailed() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));

            // act
            orderCompensationService.compensate(order.getId());

            // assert
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(Order.Status.FAILED);
        }

        @DisplayName("쿠폰 없이 생성된 주문이면 쿠폰 복원을 시도하지 않는다.")
        @Test
        void doesNotCallRestore_whenNoCoupon() {
            // arrange
            Order order = orderRepository.save(Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1))));

            // act
            orderCompensationService.compensate(order.getId());

            // assert
            then(issuedCouponService).should(never()).restore(anyLong(), anyLong());
        }

        @DisplayName("쿠폰을 사용한 주문이면 해당 쿠폰을 복원한다.")
        @Test
        void callsRestore_whenCouponExists() {
            // arrange
            Order order = orderRepository.save(
                    Order.create(1L, List.of(new OrderItemSnapshot(1L, "상품", 10000L, 1)), 0L, 100L)
            );

            // act
            orderCompensationService.compensate(order.getId());

            // assert
            then(issuedCouponService).should().restore(100L, 1L);
        }

        @DisplayName("주문 상품의 재고를 수량만큼 원복한다.")
        @Test
        void callsRestoreStockWithOrderItems() {
            // arrange
            OrderInfo orderInfo = orderService.placeOrder(
                    1L, List.of(new OrderItemSnapshot(10L, "상품A", 5000L, 3))
            );

            // act
            orderCompensationService.compensate(orderInfo.id());

            // assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OrderItemInfo>> captor = ArgumentCaptor.forClass(List.class);
            then(productService).should().restoreStock(captor.capture());
            List<OrderItemInfo> passedItems = captor.getValue();
            assertAll(
                    () -> assertThat(passedItems).hasSize(1),
                    () -> assertThat(passedItems.get(0).productId()).isEqualTo(10L),
                    () -> assertThat(passedItems.get(0).quantity()).isEqualTo(3)
            );
        }
    }
}

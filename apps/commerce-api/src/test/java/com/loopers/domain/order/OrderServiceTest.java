package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        orderService = new OrderService(orderRepository);
    }

    private List<OrderItem> createOrderItems() {
        return List.of(
                OrderItem.snapshot(1L, "에어맥스", "나이키", 150000, 2),
                OrderItem.snapshot(2L, "슈퍼스타", "아디다스", 120000, 1)
        );
    }

    private Order createOrder() {
        return Order.place(1L, "ORD-20260222-001", createOrderItems(),
                "홍길동", "010-1234-5678",
                "김철수", "010-9876-5432",
                "06234", "서울시 강남구 테헤란로 123", "4층 401호");
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_주문이_생성된다() {
            // arrange
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Order order = orderService.create(1L, "ORD-20260222-001", createOrderItems(),
                    "홍길동", "010-1234-5678",
                    "김철수", "010-9876-5432",
                    "06234", "서울시 강남구 테헤란로 123", "4층 401호");

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            orderService.create(1L, "ORD-20260222-001", createOrderItems(),
                    "홍길동", "010-1234-5678",
                    "김철수", "010-9876-5432",
                    "06234", "서울시 강남구 테헤란로 123", "4층 401호");

            // assert
            verify(orderRepository).save(any(Order.class));
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class 단건조회 {

        @Test
        void 존재하지_않는_주문이면_예외가_발생한다() {
            // arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> orderService.getById(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.ORDER_NOT_FOUND);
        }

        @Test
        void 존재하는_주문이면_반환한다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // act
            Order result = orderService.getById(1L);

            // assert
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class 취소 {

        @Test
        void 존재하지_않는_주문이면_예외가_발생한다() {
            // arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> orderService.cancel(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.ORDER_NOT_FOUND);
        }

        @Test
        void 본인_주문이_아니면_예외가_발생한다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // act & assert
            assertThatThrownBy(() -> orderService.cancel(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_취소된_주문을_반환한다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Order result = orderService.cancel(1L, 1L);

            // assert
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    @DisplayName("주문을 상세 조회할 때,")
    @Nested
    class 상세조회 {

        @Test
        void 존재하지_않는_주문이면_예외가_발생한다() {
            // arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.ORDER_NOT_FOUND);
        }

        @Test
        void 본인_주문이_아니면_예외가_발생한다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_주문을_반환한다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // act
            Order result = orderService.getOrder(1L, 1L);

            // assert
            assertThat(result.getOrderNumber()).isEqualTo("ORD-20260222-001");
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class 확정 {

        @Test
        void 존재하지_않는_주문이면_예외가_발생한다() {
            // arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> orderService.confirm(1L, 100L, "CARD"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(OrderErrorType.ORDER_NOT_FOUND);
        }

        @Test
        void 유효한_요청이면_confirm이_호출된다() {
            // arrange
            Order order = createOrder();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // act
            orderService.confirm(1L, 100L, "CARD");

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }
}

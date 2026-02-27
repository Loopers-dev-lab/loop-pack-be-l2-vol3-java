package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.order.service.OrderService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("주문 상품이 비어있으면 예외가 발생한다")
        @Test
        void throwsException_whenOrderProductsEmpty() {
            // arrange
            OrderCommand.Create command = new OrderCommand.Create(1L, List.of());

            // act & assert - Orders.create() validates internally
            assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
        }

        @DisplayName("정상적으로 주문을 생성한다")
        @Test
        void createsOrder_andCallsSave() {
            // arrange
            OrderProduct orderProduct = OrderProduct.create(1L, "상품명", 10000, 2);
            OrderCommand.Create command = new OrderCommand.Create(1L, List.of(orderProduct));
            Orders savedOrders = Orders.reconstruct(1L, 1L, 20000, List.of(orderProduct));
            when(orderRepository.save(any(Orders.class))).thenReturn(savedOrders);

            // act
            Orders result = orderService.createOrder(command);

            // assert
            verify(orderRepository).save(any(Orders.class));
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getMemberId()).isEqualTo(1L);
        }
    }

    @DisplayName("기간별 주문 조회")
    @Nested
    class GetOrders {

        @DisplayName("조회 결과가 없으면 빈 리스트를 반환한다")
        @Test
        void returnsEmptyList_whenNoOrdersFound() {
            // arrange
            LocalDateTime startAt = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endAt = LocalDateTime.of(2025, 12, 31, 23, 59);
            OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(1L, startAt, endAt);
            when(orderRepository.findByMemberIdAndCreatedAtBetween(1L, startAt, endAt))
                    .thenReturn(List.of());

            // act
            List<Orders> result = orderService.getOrders(command);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("정상적으로 기간별 주문 목록을 조회한다")
        @Test
        void returnsOrders_forGivenPeriod() {
            // arrange
            OrderProduct orderProduct = OrderProduct.create(1L, "상품명", 10000, 1);
            Orders orders = Orders.reconstruct(1L, 1L, 10000, List.of(orderProduct));
            LocalDateTime startAt = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endAt = LocalDateTime.of(2025, 12, 31, 23, 59);
            OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(1L, startAt, endAt);
            when(orderRepository.findByMemberIdAndCreatedAtBetween(1L, startAt, endAt))
                    .thenReturn(List.of(orders));

            // act
            List<Orders> result = orderService.getOrders(command);

            // assert
            verify(orderRepository).findByMemberIdAndCreatedAtBetween(1L, startAt, endAt);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }
    }

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("존재하지 않는 주문 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenOrderNotFound() {
            // arrange
            OrderCommand.GetByMember command = new OrderCommand.GetByMember(1L, 999L);
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("다른 회원의 주문을 조회하면 예외가 발생한다")
        @Test
        void throwsException_whenOrderBelongsToOtherMember() {
            // arrange
            OrderProduct orderProduct = OrderProduct.create(1L, "상품명", 10000, 1);
            Orders orders = Orders.reconstruct(1L, 2L, 10000, List.of(orderProduct));
            OrderCommand.GetByMember command = new OrderCommand.GetByMember(1L, 1L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(orders));

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 주문을 조회한다")
        @Test
        void returnsOrder_whenMemberIdMatches() {
            // arrange
            OrderProduct orderProduct = OrderProduct.create(1L, "상품명", 10000, 1);
            Orders orders = Orders.reconstruct(1L, 1L, 10000, List.of(orderProduct));
            OrderCommand.GetByMember command = new OrderCommand.GetByMember(1L, 1L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(orders));

            // act
            Orders result = orderService.getOrder(command);

            // assert
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getMemberId()).isEqualTo(1L);
        }
    }
}

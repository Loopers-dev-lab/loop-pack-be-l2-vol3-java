package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    @DisplayName("주문할 때, ")
    @Nested
    class PlaceOrder {

        @DisplayName("유효한 정보가 주어지면, 주문이 저장된다.")
        @Test
        void savesOrder_whenValidInfoIsProvided() {
            // arrange
            OrderItemModel item = new OrderItemModel(1L, "에어맥스", 150000L, 1);
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderModel result = orderService.placeOrder(1L, List.of(item));

            // assert
            assertThat(result.getTotalAmount()).isEqualTo(150000L);
            verify(orderRepository).save(any(OrderModel.class));
        }
    }

    @DisplayName("내 주문 목록을 조회할 때, ")
    @Nested
    class GetMyOrders {

        @DisplayName("시작일이 종료일보다 이후면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStartAtIsAfterEndAt() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.getMyOrders(1L, LocalDate.of(2026, 2, 11), LocalDate.of(2026, 2, 10));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 기간이 주어지면, 주문 목록을 반환한다.")
        @Test
        void returnsOrders_whenPeriodIsValid() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            given(orderRepository.findAllByUserIdAndPeriod(any(), any(), any())).willReturn(List.of(order));

            // act
            List<OrderModel> result = orderService.getMyOrders(
                1L,
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 10)
            );

            // assert
            assertThat(result).hasSize(1);
            verify(orderRepository).findAllByUserIdAndPeriod(any(), any(), any());
        }
    }

    @DisplayName("내 주문 상세를 조회할 때, ")
    @Nested
    class GetMyOrder {

        @DisplayName("주문이 존재하면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            given(orderRepository.findDetailByIdAndUserId(1L, 1L)).willReturn(Optional.of(order));

            // act
            OrderModel result = orderService.getMyOrder(1L, 1L);

            // assert
            assertThat(result).isEqualTo(order);
        }

        @DisplayName("주문이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenOrderDoesNotExist() {
            // arrange
            given(orderRepository.findDetailByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.getMyOrder(1L, 1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("관리자 주문 조회할 때, ")
    @Nested
    class AdminGetOrder {

        @DisplayName("주문이 존재하면, 주문을 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            given(orderRepository.findDetailById(1L)).willReturn(Optional.of(order));

            // act
            OrderModel result = orderService.getOrder(1L);

            // assert
            assertThat(result).isEqualTo(order);
        }

        @DisplayName("주문이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenOrderDoesNotExist() {
            // arrange
            given(orderRepository.findDetailById(1L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.getOrder(1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("관리자 주문 목록 조회할 때, ")
    @Nested
    class AdminGetAll {

        @DisplayName("페이징된 주문 목록을 반환한다.")
        @Test
        void returnsPagedOrders() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            Page<OrderModel> page = new PageImpl<>(List.of(order), pageable, 1);
            given(orderRepository.findAll(pageable)).willReturn(page);

            // act
            Page<OrderModel> result = orderService.getAll(pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(orderRepository).findAll(pageable);
        }
    }
}

package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @DisplayName("주문 저장")
    @Nested
    class Save {

        @DisplayName("주문을 저장한다")
        @Test
        void savesOrder() {
            // arrange
            OrderModel order = new OrderModel(1L);
            given(orderRepository.save(any(OrderModel.class))).willReturn(order);
            // act
            OrderModel result = orderService.save(order);
            // assert
            assertThat(result.getMemberId()).isEqualTo(1L);
            then(orderRepository).should().save(order);
        }
    }

    @DisplayName("주문 조회")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문을 반환한다")
        @Test
        void returnsForExistingId() {
            // arrange
            Long id = 1L;
            OrderModel order = new OrderModel(1L);
            ReflectionTestUtils.setField(order, "id", id);
            given(orderRepository.findById(id)).willReturn(Optional.of(order));
            // act
            OrderModel result = orderService.getById(id);
            // assert
            assertThat(result.getMemberId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 ID면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsOnNonExistentId() {
            // arrange
            Long id = 999L;
            given(orderRepository.findById(id)).willReturn(Optional.empty());
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                orderService.getById(id);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

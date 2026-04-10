package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.loopers.domain.order.event.OrderCompletedEvent;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("OrderPointHandler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderPointHandlerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private OrderPointHandler orderPointHandler;

    @DisplayName("주문 완료 포인트를 적립할 때, ")
    @Nested
    class Handle {

        @DisplayName("주문 금액의 2%를 포인트로 적립한다.")
        @Test
        void handle_addsPointsBasedOnTotalPrice() {
            // arrange
            OrderCompletedEvent event = new OrderCompletedEvent(1L, 100L, 50000);

            // act
            orderPointHandler.handle(event);

            // assert
            verify(userService).addPoint(100L, 1000L);
        }

        @DisplayName("포인트가 0원이면 적립하지 않는다.")
        @Test
        void handle_whenPointIsZero_doesNotAddPoint() {
            // arrange
            OrderCompletedEvent event = new OrderCompletedEvent(1L, 100L, 49);

            // act
            orderPointHandler.handle(event);

            // assert
            verify(userService, never()).addPoint(anyLong(), anyLong());
        }

        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다.")
        @Test
        void handle_whenExceptionOccurs_doesNotPropagate() {
            // arrange
            doThrow(new RuntimeException("DB error"))
                    .when(userService).addPoint(anyLong(), anyLong());

            // act & assert
            assertThatCode(() -> orderPointHandler.handle(new OrderCompletedEvent(1L, 100L, 50000)))
                    .doesNotThrowAnyException();
        }
    }
}

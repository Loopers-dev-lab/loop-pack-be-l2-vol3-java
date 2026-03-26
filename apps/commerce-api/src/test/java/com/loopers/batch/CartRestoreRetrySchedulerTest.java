package com.loopers.batch;

import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartRestoreRetryScheduler 단위 테스트")
class CartRestoreRetrySchedulerTest {

    @Mock private OrderService orderService;
    @Mock private CartService cartService;
    @InjectMocks private CartRestoreRetryScheduler scheduler;

    @Test
    @DisplayName("미복원 건 없으면 restoreFromOrder 호출 안 함")
    void retry_NoUnrestored_ShouldSkip() {
        when(orderService.findUnrestoredDirectOrders()).thenReturn(List.of());

        scheduler.retryFailedCartRestores();

        verify(cartService, never()).restoreFromOrder(anyLong(), anyList());
    }

    @Test
    @DisplayName("미복원 건 감지 시 복원 처리")
    void retry_WithUnrestored_ShouldRestore() {
        OrderModel order = mock(OrderModel.class);
        when(order.getOrderId()).thenReturn(1L);
        when(order.getUserId()).thenReturn(1L);
        when(orderService.findUnrestoredDirectOrders()).thenReturn(List.of(order));
        when(orderService.existsCartRestore(1L)).thenReturn(false);
        when(orderService.findOrderItems(1L)).thenReturn(List.of());

        scheduler.retryFailedCartRestores();

        verify(orderService).saveCartRestore(any());
    }

    @Test
    @DisplayName("이미 복원된 건은 skip (멱등)")
    void retry_AlreadyRestored_ShouldSkip() {
        OrderModel order = mock(OrderModel.class);
        when(order.getOrderId()).thenReturn(1L);
        when(orderService.findUnrestoredDirectOrders()).thenReturn(List.of(order));
        when(orderService.existsCartRestore(1L)).thenReturn(true);

        scheduler.retryFailedCartRestores();

        verify(cartService, never()).restoreFromOrder(anyLong(), anyList());
    }
}

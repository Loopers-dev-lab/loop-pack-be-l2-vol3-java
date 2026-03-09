package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAppService 단위 테스트")
class OrderAppServiceTest {

    @Mock
    OrderService orderService;

    @Mock
    UserService userService;

    @InjectMocks
    OrderAppService orderAppService;

    private UserModel mockAuthenticate() {
        UserModel user = mock(UserModel.class);
        when(user.getUserId()).thenReturn("user-1");
        when(userService.authenticate("login1", "pw1")).thenReturn(user);
        return user;
    }

    @Test
    @DisplayName("주문 상세 조회 시 인증 후 스냅샷 포함 OrderInfo를 반환한다")
    void getOrderDetail_ShouldReturnOrderInfoWithSnapshots() {
        mockAuthenticate();
        OrderModel order = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(30000));
        when(orderService.findByIdAndUserId("order-1", "user-1")).thenReturn(order);
        when(orderService.findOrderItems(any())).thenReturn(List.of());

        OrderInfo result = orderAppService.getOrderDetail("login1", "pw1", "order-1");

        assertThat(result).isNotNull();
        verify(userService).authenticate("login1", "pw1");
        verify(orderService).findByIdAndUserId("order-1", "user-1");
    }

    @Test
    @DisplayName("내 주문 목록 조회 시 인증 후 OrderInfo 리스트를 반환한다")
    void getOrders_ShouldReturnOrderInfoList() {
        mockAuthenticate();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        OrderModel order = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(10000));
        when(orderService.findAllByUserId("user-1", start, end)).thenReturn(List.of(order));
        when(orderService.findOrderItems(any())).thenReturn(List.of());

        List<OrderInfo> result = orderAppService.getOrders("login1", "pw1", start, end);

        assertThat(result).hasSize(1);
        verify(userService).authenticate("login1", "pw1");
        verify(orderService).findAllByUserId("user-1", start, end);
    }

    @Test
    @DisplayName("관리자용 주문 상세 조회 시 인증 없이 OrderInfo를 반환한다")
    void findOrderById_ShouldReturnOrderInfo() {
        OrderModel order = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(20000));
        when(orderService.findOrderById("order-1")).thenReturn(order);
        when(orderService.findOrderItems(any())).thenReturn(List.of());

        OrderInfo result = orderAppService.findOrderById("order-1");

        assertThat(result).isNotNull();
        verify(orderService).findOrderById("order-1");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("관리자용 주문 목록 조회 시 인증 없이 OrderInfo 리스트를 반환한다")
    void findAllOrders_ShouldReturnOrderInfoList() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        OrderModel order = OrderModel.create("user-1", OrderType.CART, BigDecimal.valueOf(15000));
        when(orderService.findAllOrders(start, end)).thenReturn(List.of(order));
        when(orderService.findOrderItems(any())).thenReturn(List.of());

        List<OrderInfo> result = orderAppService.findAllOrders(start, end);

        assertThat(result).hasSize(1);
        verify(orderService).findAllOrders(start, end);
        verifyNoInteractions(userService);
    }
}

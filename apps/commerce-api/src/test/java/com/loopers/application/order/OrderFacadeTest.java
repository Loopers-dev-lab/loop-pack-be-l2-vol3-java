package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.domain.waitingroom.WaitingRoomErrorCode;
import com.loopers.domain.waitingroom.WaitingRoomService;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("OrderFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private OrderService orderService;

    @Mock
    private CouponService couponService;

    @Mock
    private UserService userService;

    @Mock
    private WaitingRoomService waitingRoomService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderFacade orderFacade;

    @DisplayName("토큰으로 주문을 생성할 때, ")
    @Nested
    class CreateOrderWithToken {

        @DisplayName("토큰 검증 → 주문 생성 → 토큰 삭제가 순서대로 수행된다.")
        @Test
        void createOrderWithToken_success() {
            // arrange
            Long brandId = 1L;
            String token = "valid-token";
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 25000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 25000, 1, "상품A", "브랜드A")));
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 25000)));

            // act
            orderFacade.createOrderWithToken(1L, token, criteria);

            // assert
            assertAll(
                    () -> verify(waitingRoomService).validateToken(1L, token),
                    () -> verify(orderService).createOrder(anyLong(), anyList()),
                    () -> verify(waitingRoomService).completeEntry(1L));
        }

        @DisplayName("토큰이 유효하지 않으면 주문이 진행되지 않는다.")
        @Test
        void createOrderWithToken_invalidToken_throwsException() {
            // arrange
            doAnswer(invocation -> {
                throw new CoreException(WaitingRoomErrorCode.INVALID_TOKEN);
            }).when(waitingRoomService).validateToken(1L, "bad-token");

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 25000)));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrderWithToken(1L, "bad-token", criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.INVALID_TOKEN));
            verify(orderService, never()).createOrder(anyLong(), anyList());
        }
    }

    @DisplayName("회원 주문 목록을 조회할 때, ")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 본인 주문 목록을 반환한다")
        @Test
        void getMyOrders_returnsOrders() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();
            when(orderService.getOrdersByUserIdAndPeriod(1L, startAt, endAt))
                    .thenReturn(List.of(order));

            // act
            List<OrderResult.OrderSummary> results =
                    orderFacade.getMyOrders(1L, new OrderCriteria.ListByDate(startAt, endAt));

            // assert
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).totalPrice()).isEqualTo(50000));
        }
    }

    @DisplayName("회원 아이템 취소할 때, ")
    @Nested
    class CancelMyOrderItem {

        @DisplayName("소유자 검증 + 아이템 취소 + 재고 복구가 수행된다")
        @Test
        void cancelMyOrderItem_success() {
            // arrange
            when(orderService.getByIdWithLock(1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 25000, 2, "상품A", "브랜드A"))));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 2, false));

            // act
            orderFacade.cancelMyOrderItem(1L, 1L, 100L);

            // assert
            assertAll(
                    () -> verify(orderService).getByIdWithLock(1L),
                    () -> verify(orderService).cancelItem(any(OrderModel.class), eq(100L)),
                    () -> verify(productService).increaseStock(10L, 2));
        }

        @DisplayName("전체 취소 시 쿠폰이 복원된다")
        @Test
        void cancelOrderItem_whenAllCancelled_restoresCoupon() {
            // arrange
            when(orderService.getByIdWithLock(1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")), 5000));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 1, true));

            // act
            orderFacade.cancelMyOrderItem(1L, 1L, 100L);

            // assert
            assertAll(
                    () -> verify(couponService).restoreByOrderId(1L),
                    () -> verify(productService).increaseStock(10L, 1));
        }
    }
}

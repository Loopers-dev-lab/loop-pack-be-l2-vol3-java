package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderFacade 단위 테스트.
 * 상품 검증·재고 차감·쿠폰 적용·주문 생성 오케스트레이션을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;
    private static final Long PRODUCT_ID = 100L;
    private static final List<CreateOrderItemParam> PARAMS = List.of(new CreateOrderItemParam(PRODUCT_ID, 1, null));

    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot(
            PRODUCT_ID, "상품", Money.of(new BigDecimal("10000")));

    @Mock
    private OrderService orderService;
    @Mock
    private ProductService productService;
    @Mock
    private CouponService couponService;
    @Mock
    private OrderEntryTokenGate orderEntryTokenGate;
    @InjectMocks
    private OrderFacade orderFacade;

    private static OrderModel createOrderModel() {
        OrderModel order = OrderModel.create(USER_ID, new BigDecimal("10000"),
                new BigDecimal("1000"), new BigDecimal("9000"), COUPON_ID);
        order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
        return order;
    }

    private static OrderInfo createOrderInfo() {
        return OrderInfo.from(createOrderModel());
    }

    @DisplayName("placeOrder (쿠폰 적용) 시")
    @Nested
    class PlaceOrderWithCouponRetry {

        @Test
        @DisplayName("상품 스냅샷·쿠폰 할인·주문 생성이 한 번씩 호출된다.")
        void placeOrder_withCoupon_shouldCallServicesOnce() {
            OrderModel orderModel = createOrderModel();
            OrderInfo expected = OrderInfo.from(orderModel);
            when(productService.validateDecreaseStockAndGetSnapshots(any())).thenReturn(List.of(SNAPSHOT));
            when(couponService.validateAndUse(eq(COUPON_ID), eq(USER_ID), any())).thenReturn(
                    new com.loopers.domain.coupon.CouponDiscount(
                            new BigDecimal("10000"),
                            new BigDecimal("1000"),
                            new BigDecimal("9000")
                    )
            );
            when(orderService.create(eq(USER_ID), any(), any(), any(), eq(COUPON_ID)))
                    .thenReturn(orderModel);

            OrderInfo result = orderFacade.placeOrder(USER_ID, PARAMS, COUPON_ID);

            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            verify(orderEntryTokenGate).verifyAndConsumeIfRequired(USER_ID, null);
            verify(productService, times(1)).validateDecreaseStockAndGetSnapshots(any());
            verify(couponService, times(1)).validateAndUse(eq(COUPON_ID), eq(USER_ID), any());
            verify(orderService, times(1)).create(eq(USER_ID), any(), any(), any(), eq(COUPON_ID));
        }
    }

    @DisplayName("placeOrder (쿠폰 미적용) 시")
    @Nested
    class PlaceOrderWithoutCoupon {

        @Test
        @DisplayName("쿠폰이 없으면 쿠폰 서비스는 호출되지 않는다.")
        void placeOrder_withoutCoupon_callsCouponServiceNever() {
            OrderModel orderModel = createOrderModel();
            OrderInfo expected = OrderInfo.from(orderModel);
            when(productService.validateDecreaseStockAndGetSnapshots(any())).thenReturn(List.of(SNAPSHOT));
            when(orderService.create(eq(USER_ID), any(), any(), any(), eq(null)))
                    .thenReturn(orderModel);

            OrderInfo result = orderFacade.placeOrder(USER_ID, PARAMS, null);

            assertThat(result).isEqualTo(expected);
            verify(orderEntryTokenGate).verifyAndConsumeIfRequired(USER_ID, null);
            verify(couponService, times(0)).validateAndUse(any(), any(), any());
        }
    }
}

package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.coupon.CouponService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderFacade 단위 테스트.
 * 쿠폰 낙관락 재시도(최대 1회) 동작을 검증. (05-transaction-query §3.4, §9.1, output/쿠폰락_트레이드오프_전문가토론.md)
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
    private OrderFacade self;

    @InjectMocks
    private OrderFacade orderFacade;

    private static OrderInfo createOrderInfo() {
        OrderModel order = OrderModel.create(USER_ID, new BigDecimal("10000"),
                new BigDecimal("1000"), new BigDecimal("9000"), COUPON_ID);
        order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
        return OrderInfo.from(order);
    }

    @DisplayName("placeOrder (쿠폰 적용) 시 낙관락 재시도")
    @Nested
    class PlaceOrderWithCouponRetry {

        @Test
        @DisplayName("첫 시도에서 OptimisticLockException 발생 시 1회 재시도 후 성공한다.")
        void placeOrder_withCoupon_whenFirstCallThrowsOptimisticLock_thenRetriesOnceAndSucceeds() {
            OrderInfo expected = createOrderInfo();
            when(self.doPlaceOrder(any(), any(), eq(COUPON_ID)))
                    .thenThrow(new ObjectOptimisticLockingFailureException("version conflict", new OptimisticLockException()))
                    .thenReturn(expected);

            OrderInfo result = orderFacade.placeOrder(USER_ID, PARAMS, COUPON_ID);

            assertThat(result).isEqualTo(expected);
            verify(self, times(2)).doPlaceOrder(any(), any(), eq(COUPON_ID));
        }

        @Test
        @DisplayName("첫 시도와 재시도 모두 OptimisticLockException이면 CONFLICT(409) + '잠시 후 다시 시도해 주세요'를 던진다.")
        void placeOrder_withCoupon_whenBothCallsThrowOptimisticLock_thenThrowsConflictWithRetryMessage() {
            ObjectOptimisticLockingFailureException ole = new ObjectOptimisticLockingFailureException(
                    "version conflict", new OptimisticLockException());
            when(self.doPlaceOrder(any(), any(), eq(COUPON_ID)))
                    .thenThrow(ole)
                    .thenThrow(ole);

            CoreException ex = assertThrows(CoreException.class,
                    () -> orderFacade.placeOrder(USER_ID, PARAMS, COUPON_ID));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(ex.getCustomMessage()).isEqualTo("잠시 후 다시 시도해 주세요.");
            verify(self, times(2)).doPlaceOrder(any(), any(), eq(COUPON_ID));
        }

        @Test
        @DisplayName("JPA OptimisticLockException도 재시도 후 실패 시 CONFLICT로 변환한다.")
        void placeOrder_withCoupon_whenBothCallsThrowJpaOptimisticLock_thenThrowsConflict() {
            when(self.doPlaceOrder(any(), any(), eq(COUPON_ID)))
                    .thenThrow(new OptimisticLockException())
                    .thenThrow(new OptimisticLockException());

            CoreException ex = assertThrows(CoreException.class,
                    () -> orderFacade.placeOrder(USER_ID, PARAMS, COUPON_ID));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(ex.getCustomMessage()).isEqualTo("잠시 후 다시 시도해 주세요.");
            verify(self, times(2)).doPlaceOrder(any(), any(), eq(COUPON_ID));
        }
    }

    @DisplayName("placeOrder (쿠폰 미적용) 시")
    @Nested
    class PlaceOrderWithoutCoupon {

        @Test
        @DisplayName("쿠폰이 없으면 재시도 없이 doPlaceOrder를 한 번만 호출한다.")
        void placeOrder_withoutCoupon_callsDoPlaceOrderOnce() {
            OrderInfo expected = createOrderInfo();
            when(self.doPlaceOrder(any(), any(), eq(null))).thenReturn(expected);

            OrderInfo result = orderFacade.placeOrder(USER_ID, PARAMS, null);

            assertThat(result).isEqualTo(expected);
            verify(self, times(1)).doPlaceOrder(any(), any(), eq(null));
        }
    }
}

package com.loopers.application.order;

import com.loopers.application.coupon.CouponApp;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    @Mock
    private OrderApp orderApp;

    @Mock
    private CouponApp couponApp;

    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        orderFacade = new OrderFacade(orderApp, couponApp);
    }

    @Nested
    @DisplayName("주문 생성 (createOrder)")
    class CreateOrder {

        @Test
        @DisplayName("쿠폰 없는 주문 - discountAmount=0, refUserCouponId=null")
        void createOrder_withoutCoupon_success() {
            // given
            Long memberId = 1L;
            List<OrderItemCommand> items = List.of(new OrderItemCommand("prod1", 2));
            OrderInfo expectedInfo = createOrderInfo(BigDecimal.ZERO, null);
            when(orderApp.createOrder(memberId, items, BigDecimal.ZERO, null)).thenReturn(expectedInfo);

            // when
            OrderInfo result = orderFacade.createOrder(memberId, items, null);

            // then
            assertThat(result).isEqualTo(expectedInfo);
            verifyNoInteractions(couponApp);
        }

        @Test
        @DisplayName("쿠폰 있는 주문 - discountAmount 적용 확인")
        void createOrder_withCoupon_appliesDiscount() {
            // given
            Long memberId = 1L;
            Long userCouponId = 42L;
            List<OrderItemCommand> items = List.of(new OrderItemCommand("prod1", 2));
            BigDecimal originalAmount = BigDecimal.valueOf(20000);
            BigDecimal discountAmount = BigDecimal.valueOf(2000);
            Long userCouponPkId = 42L;

            when(orderApp.calculateOriginalAmount(items)).thenReturn(originalAmount);
            when(couponApp.calculateDiscount(userCouponId, memberId, originalAmount)).thenReturn(discountAmount);
            when(couponApp.useUserCoupon(userCouponId)).thenReturn(userCouponPkId);
            OrderInfo expectedInfo = createOrderInfo(discountAmount, userCouponPkId);
            when(orderApp.createOrder(memberId, items, discountAmount, userCouponPkId)).thenReturn(expectedInfo);

            // when
            OrderInfo result = orderFacade.createOrder(memberId, items, userCouponId);

            // then
            assertThat(result).isEqualTo(expectedInfo);
            assertThat(result.discountAmount()).isEqualByComparingTo(discountAmount);
            assertThat(result.refUserCouponId()).isEqualTo(userCouponPkId);
        }

        @Test
        @DisplayName("만료 쿠폰으로 주문 시 실패")
        void createOrder_expiredCoupon_throws() {
            // given
            Long memberId = 1L;
            Long userCouponId = 42L;
            List<OrderItemCommand> items = List.of(new OrderItemCommand("prod1", 2));
            BigDecimal originalAmount = BigDecimal.valueOf(20000);

            when(orderApp.calculateOriginalAmount(items)).thenReturn(originalAmount);
            when(couponApp.calculateDiscount(userCouponId, memberId, originalAmount))
                    .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다."));

            // when & then
            assertThatThrownBy(() -> orderFacade.createOrder(memberId, items, userCouponId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("주문 취소 (cancelOrder)")
    class CancelOrder {

        @Test
        @DisplayName("쿠폰 없는 주문 취소 - 쿠폰 복원 없음")
        void cancelOrder_withoutCoupon_noCouponRestore() {
            // given
            Long memberId = 1L;
            String orderId = "00000000-0000-0000-0000-000000000002";
            OrderInfo orderInfo = createOrderInfo(BigDecimal.ZERO, null);
            when(orderApp.cancelOrder(memberId, orderId)).thenReturn(orderInfo);

            // when
            orderFacade.cancelOrder(memberId, orderId);

            // then
            verify(orderApp).cancelOrder(memberId, orderId);
            verifyNoInteractions(couponApp);
        }

        @Test
        @DisplayName("쿠폰 있는 주문 취소 - 쿠폰 복원 호출")
        void cancelOrder_withCoupon_restoresCoupon() {
            // given
            Long memberId = 1L;
            String orderId = "00000000-0000-0000-0000-000000000002";
            Long userCouponPkId = 42L;
            OrderInfo orderInfo = createOrderInfo(BigDecimal.valueOf(2000), userCouponPkId);
            when(orderApp.cancelOrder(memberId, orderId)).thenReturn(orderInfo);

            // when
            orderFacade.cancelOrder(memberId, orderId);

            // then
            verify(orderApp).cancelOrder(memberId, orderId);
            verify(couponApp).restoreUserCoupon(userCouponPkId);
        }
    }

    private OrderInfo createOrderInfo(BigDecimal discountAmount, Long refUserCouponId) {
        BigDecimal originalAmount = BigDecimal.valueOf(20000);
        BigDecimal finalAmount = originalAmount.subtract(discountAmount);
        return new OrderInfo(1L, "00000000-0000-0000-0000-000000000002", 1L, "PENDING",
                originalAmount, discountAmount, finalAmount, refUserCouponId, List.of());
    }
}

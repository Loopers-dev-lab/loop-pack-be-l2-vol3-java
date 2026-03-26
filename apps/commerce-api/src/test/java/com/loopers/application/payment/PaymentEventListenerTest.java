package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener 단위 테스트")
class PaymentEventListenerTest {

    @Mock
    private OrderHistoryService orderHistoryService;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Nested
    @DisplayName("결제 완료 이벤트 처리")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("PaymentCompletedEvent 수신 시 OrderHistory를 기록한다")
        void shouldRecordOrderHistory() {
            // given
            PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 100L, "txn-key-123", "결제 완료");

            // when
            paymentEventListener.handlePaymentCompleted(event);

            // then
            then(orderHistoryService).should().recordHistory(
                    1L, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, "결제 완료"
            );
        }
    }

    @Nested
    @DisplayName("결제 실패 이벤트 처리")
    class HandlePaymentFailed {

        @Test
        @DisplayName("PaymentFailedEvent 수신 시 OrderHistory를 기록한다")
        void shouldRecordOrderHistory() {
            // given
            PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, null, "잔액 부족");

            // when
            paymentEventListener.handlePaymentFailed(event);

            // then
            then(orderHistoryService).should().recordHistory(
                    1L, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, "잔액 부족"
            );
        }

        @Test
        @DisplayName("PaymentFailedEvent에 userCouponId가 있으면 쿠폰을 복원한다")
        void shouldRestoreCouponWhenUserCouponIdExists() {
            // given
            PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, 50L, "잔액 부족");

            // when
            paymentEventListener.handlePaymentFailed(event);

            // then
            then(couponService).should().restoreUserCoupon(50L);
        }

        @Test
        @DisplayName("PaymentFailedEvent에 userCouponId가 null이면 쿠폰 복원하지 않는다")
        void shouldNotRestoreCouponWhenUserCouponIdIsNull() {
            // given
            PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, null, "잔액 부족");

            // when
            paymentEventListener.handlePaymentFailed(event);

            // then
            then(couponService).should(never()).restoreUserCoupon(null);
        }
    }
}

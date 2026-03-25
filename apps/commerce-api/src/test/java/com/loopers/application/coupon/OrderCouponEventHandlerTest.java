package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class OrderCouponEventHandlerTest {

    UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);

    OrderCouponEventHandler handler = new OrderCouponEventHandler(userCouponRepository);

    @DisplayName("주문 생성 이벤트 수신 시, ")
    @Nested
    class Handle {

        @DisplayName("쿠폰이 포함된 주문이면, 쿠폰 상태가 사용됨으로 변경된다.")
        @Test
        void usesCoupon_whenOrderCreatedEventHasCoupon() {
            // arrange
            Long userId = 1L;
            Long userCouponId = 10L;
            OrderEvent.Created event = new OrderEvent.Created(userId, "20260325-ABCDEF", 90000L, userCouponId, List.of());

            UserCoupon userCoupon = mock(UserCoupon.class);
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

            // act
            handler.handle(event);

            // assert
            verify(userCoupon).use(userId);
            verify(userCouponRepository).save(userCoupon);
        }

        @DisplayName("쿠폰이 없는 주문이면, 쿠폰 처리를 하지 않는다.")
        @Test
        void doesNothing_whenOrderCreatedEventHasNoCoupon() {
            // arrange
            OrderEvent.Created event = new OrderEvent.Created(1L, "20260325-ABCDEF", 150000L, null, List.of());

            // act
            handler.handle(event);

            // assert
            verify(userCouponRepository, never()).findById(any());
            verify(userCouponRepository, never()).save(any());
        }
    }
}

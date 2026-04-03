package com.loopers.application.logging;

import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
class UserActivityEventListenerTest {

    @InjectMocks
    private UserActivityEventListener listener;

    @DisplayName("주문 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsOrderPlacedEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handleOrderPlaced(new OrderPlacedEvent(1L, 1L, 50000L))
        );
    }

    @DisplayName("결제 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsPaymentCompletedEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handlePaymentCompleted(new PaymentCompletedEvent(1L, 1L, 1L, true))
        );
    }

    @DisplayName("좋아요 이벤트 수신 시 로깅 처리한다")
    @Test
    void logsLikeToggledEvent() {
        assertThatNoException().isThrownBy(() ->
            listener.handleLikeToggled(new LikeToggledEvent(1L, true))
        );
    }
}

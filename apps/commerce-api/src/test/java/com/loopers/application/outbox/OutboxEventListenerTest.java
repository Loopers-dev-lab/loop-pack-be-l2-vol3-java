package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    @InjectMocks
    private OutboxEventListener listener;

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @DisplayName("LikeToggledEvent(liked=true) → LIKED 타입으로 outbox 저장")
    @Test
    void savesLikedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handleLikeToggled(new LikeToggledEvent(100L, true));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
            () -> assertThat(saved.getEventType()).isEqualTo("LIKED"),
            () -> assertThat(saved.getTopic()).isEqualTo("catalog-events"),
            () -> assertThat(saved.getAggregateType()).isEqualTo("Product"),
            () -> assertThat(saved.getAggregateId()).isEqualTo(100L),
            () -> assertThat(saved.getPartitionKey()).isEqualTo("100")
        );
    }

    @DisplayName("LikeToggledEvent(liked=false) → UNLIKED 타입으로 outbox 저장")
    @Test
    void savesUnlikedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handleLikeToggled(new LikeToggledEvent(100L, false));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UNLIKED");
    }

    @DisplayName("ProductViewedEvent → PRODUCT_VIEWED 타입으로 outbox 저장")
    @Test
    void savesProductViewedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handleProductViewed(new ProductViewedEvent(50L, 1L));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
            () -> assertThat(saved.getEventType()).isEqualTo("PRODUCT_VIEWED"),
            () -> assertThat(saved.getTopic()).isEqualTo("catalog-events"),
            () -> assertThat(saved.getPartitionKey()).isEqualTo("50")
        );
    }

    @DisplayName("OrderPlacedEvent → ORDER_PLACED 타입으로 outbox 저장")
    @Test
    void savesOrderPlacedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handleOrderPlaced(new OrderPlacedEvent(10L, 1L, 50000L));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
            () -> assertThat(saved.getEventType()).isEqualTo("ORDER_PLACED"),
            () -> assertThat(saved.getTopic()).isEqualTo("order-events")
        );
    }

    @DisplayName("PaymentCompletedEvent → PAYMENT_COMPLETED 타입으로 outbox 저장")
    @Test
    void savesPaymentCompletedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handlePaymentCompleted(new PaymentCompletedEvent(1L, 10L, 1L, true));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
            () -> assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED"),
            () -> assertThat(saved.getTopic()).isEqualTo("order-events"),
            () -> assertThat(saved.getPartitionKey()).isEqualTo("10")
        );
    }

    @DisplayName("CouponIssueRequestedEvent → COUPON_ISSUE_REQUESTED 타입으로 outbox 저장")
    @Test
    void savesCouponIssueRequestedEvent() {
        // given
        given(outboxRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        listener.handleCouponIssueRequested(new CouponIssueRequestedEvent(5L, 1L));

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
            () -> assertThat(saved.getEventType()).isEqualTo("COUPON_ISSUE_REQUESTED"),
            () -> assertThat(saved.getTopic()).isEqualTo("coupon-issue-requests"),
            () -> assertThat(saved.getPartitionKey()).isEqualTo("5")
        );
    }
}

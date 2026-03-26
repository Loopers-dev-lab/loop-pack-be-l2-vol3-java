package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.eventhandled.EventHandledService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueConsumer 단위 테스트")
class CouponIssueConsumerTest {

    @Mock
    private CouponService couponService;

    @Mock
    private EventHandledService eventHandledService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CouponIssueConsumer couponIssueConsumer;

    @Mock
    private Acknowledgment acknowledgment;

    @Nested
    @DisplayName("consume - 쿠폰 발급 이벤트 처리")
    class Consume {

        @Test
        @DisplayName("성공: 쿠폰 발급 이벤트를 처리하고 ACK한다")
        void consume_success() {
            // Given
            CouponIssueRequestMessage message = new CouponIssueRequestMessage(
                    "event-1", "COUPON_ISSUE_REQUESTED", "10",
                    "{\"userId\":1,\"couponId\":10}", ZonedDateTime.now()
            );
            given(eventHandledService.isAlreadyHandled("event-1")).willReturn(false);

            // When
            couponIssueConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(couponService).should().issueCouponWithQuantityControl(1L, 10L);
            then(eventHandledService).should().markAsHandled("event-1");
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("성공: 이미 처리된 이벤트는 건너뛴다 (멱등)")
        void consume_alreadyHandled_skips() {
            // Given
            CouponIssueRequestMessage message = new CouponIssueRequestMessage(
                    "event-1", "COUPON_ISSUE_REQUESTED", "10",
                    "{\"userId\":1,\"couponId\":10}", ZonedDateTime.now()
            );
            given(eventHandledService.isAlreadyHandled("event-1")).willReturn(true);

            // When
            couponIssueConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(couponService).should(never()).issueCouponWithQuantityControl(1L, 10L);
            then(acknowledgment).should().acknowledge();
        }
    }
}

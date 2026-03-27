package com.loopers.collector.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CouponIssueRequestCollectorListenerTest {

    @Mock
    private CouponService couponService;

    @Mock
    private Acknowledgment acknowledgment;

    private CouponIssueRequestCollectorListener listener;

    @BeforeEach
    void setUp() {
        listener = new CouponIssueRequestCollectorListener(couponService, new ObjectMapper());
    }

    @DisplayName("onMessage 시")
    @Nested
    class OnMessage {

        @Test
        void onMessage_withCouponIssueRequested_shouldProcessAndAck() {
            String payload = """
                    {
                      "eventId":"evt-1",
                      "eventType":"COUPON_ISSUE_REQUESTED",
                      "data":{"requestId":"req-1","userId":1,"couponTemplateId":10}
                    }
                    """;
            ConsumerRecord<Object, Object> record =
                    new ConsumerRecord<>("coupon-issue-requests", 0, 1L, "10", payload);

            listener.onMessage(record, acknowledgment);

            verify(couponService).processCouponIssueRequest("req-1", 1L, 10L);
            verify(acknowledgment).acknowledge();
        }

        @Test
        void onMessage_withCouponIssueRequested_withoutRequestId_shouldFallbackToIssueIfAbsent() {
            String payload = """
                    {
                      "eventId":"evt-legacy",
                      "eventType":"COUPON_ISSUE_REQUESTED",
                      "data":{"userId":1,"couponTemplateId":10}
                    }
                    """;
            ConsumerRecord<Object, Object> record =
                    new ConsumerRecord<>("coupon-issue-requests", 0, 1L, "10", payload);

            listener.onMessage(record, acknowledgment);

            verify(couponService).issueIfAbsent(1L, 10L);
            verify(acknowledgment).acknowledge();
        }

        @Test
        void onMessage_withUnknownType_shouldOnlyAck() {
            String payload = """
                    {
                      "eventId":"evt-2",
                      "eventType":"UNKNOWN",
                      "data":{"userId":1,"couponTemplateId":10}
                    }
                    """;
            ConsumerRecord<Object, Object> record =
                    new ConsumerRecord<>("coupon-issue-requests", 0, 1L, "10", payload);

            listener.onMessage(record, acknowledgment);

            verifyNoInteractions(couponService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        void onMessage_withInvalidPayload_shouldThrow() {
            ConsumerRecord<Object, Object> record =
                    new ConsumerRecord<>("coupon-issue-requests", 0, 1L, "10", "{invalid");

            assertThrows(IllegalArgumentException.class, () -> listener.onMessage(record, acknowledgment));
            verifyNoInteractions(couponService);
        }
    }
}

package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEvent 단위 테스트")
class OutboxEventTest {

    @Nested
    @DisplayName("create - Outbox 이벤트 생성")
    class Create {

        @Test
        @DisplayName("성공: OutboxEvent를 생성하면 status는 INIT이고 eventId는 UUID 형식이다")
        void create_setsInitStatusAndUuidEventId() {
            // Given
            String eventType = "PRODUCT_LIKED";
            String aggregateId = "123";
            String payload = "{\"userId\":1,\"productId\":123}";

            // When
            OutboxEvent event = OutboxEvent.create("catalog-events", eventType, aggregateId, payload);

            // Then
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.INIT);
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getEventId()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
            assertThat(event.getEventType()).isEqualTo(eventType);
            assertThat(event.getAggregateId()).isEqualTo(aggregateId);
            assertThat(event.getPayload()).isEqualTo(payload);
            assertThat(event.getOccurredAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("markAsSent - 발송 완료 처리")
    class MarkAsSent {

        @Test
        @DisplayName("성공: markAsSent 호출 시 status가 SENT로 변경된다")
        void markAsSent_changesStatusToSent() {
            // Given
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{}");

            // When
            event.markAsSent();

            // Then
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }
}

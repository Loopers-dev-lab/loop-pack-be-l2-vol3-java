package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @DisplayName("OutboxEvent.create() 를 호출할 때, ")
    @Nested
    class Create {

        @DisplayName("UUID 형식의 eventId가 생성되고, PENDING 상태로 저장되며, eventType/topic/payload가 저장된다.")
        @Test
        void createsOutboxEvent_withPendingStatusAndUuidEventId() {
            // arrange
            String eventType = "LIKE_CREATED";
            String topic = OutboxEventTopics.PRODUCT_LIKE;
            String payload = "{\"userId\":1,\"productId\":2}";
            String partitionKey = "1";

            // act
            OutboxEvent event = OutboxEvent.create(eventType, topic, payload, partitionKey);

            // assert
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getEventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getEventType()).isEqualTo(eventType);
            assertThat(event.getTopic()).isEqualTo(topic);
            assertThat(event.getPayload()).isEqualTo(payload);
        }

        @DisplayName("두 번 호출하면 서로 다른 eventId가 생성된다.")
        @Test
        void generatesDifferentEventIds_whenCalledTwice() {
            // arrange & act
            OutboxEvent event1 = OutboxEvent.create("LIKE_CREATED", OutboxEventTopics.PRODUCT_LIKE, "{}","1");
            OutboxEvent event2 = OutboxEvent.create("LIKE_CREATED", OutboxEventTopics.PRODUCT_LIKE, "{}", "1");

            // assert
            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }
    }

    @DisplayName("markSent() 를 호출할 때, ")
    @Nested
    class MarkSent {

        @DisplayName("상태가 SENT 로 변경된다.")
        @Test
        void changesStatus_toSent() {
            // arrange
            OutboxEvent event = OutboxEvent.create("LIKE_CREATED", OutboxEventTopics.PRODUCT_LIKE, "{}", "1");
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

            // act
            event.markSent();

            // assert
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }
}

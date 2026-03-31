package com.loopers.event;

import com.loopers.event.payload.ProductLikedEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    @Test
    @DisplayName("Event를 JSON으로 직렬화 후 역직렬화하면 eventId, type, payload가 보존된다")
    void serializeAndDeserializePreservesAllFields() {
        // Arrange
        ProductLikedEventPayload payload = ProductLikedEventPayload.of(100L, 1L);
        Event<EventPayload> event = Event.of(1234L, EventType.PRODUCT_LIKED, payload);

        // Act
        String json = event.toJson();
        Event<EventPayload> result = Event.fromJson(json);

        // Assert
        assertThat(result.getEventId()).isEqualTo(event.getEventId());
        assertThat(result.getType()).isEqualTo(event.getType());
        assertThat(result.getPayload()).isInstanceOf(ProductLikedEventPayload.class);

        ProductLikedEventPayload resultPayload = (ProductLikedEventPayload) result.getPayload();
        assertThat(resultPayload.getProductId()).isEqualTo(payload.getProductId());
        assertThat(resultPayload.getUserId()).isEqualTo(payload.getUserId());
    }
}

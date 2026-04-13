package com.loopers.interfaces.consumer.dto;

import java.util.List;

public class MetricsMessageDto {

    public record LikeMessage(String eventId, Long productId) {}

    public record OrderCompletedMessage(String eventId, Long orderId, List<OrderItem> orderItems) {
        public record OrderItem(Long productId, Long quantity, Long price) {}
    }

    public record ProductViewedMessage(String eventId, Long productId) {}

    public record ProductDeletedMessage(String eventId, Long productId) {}
}

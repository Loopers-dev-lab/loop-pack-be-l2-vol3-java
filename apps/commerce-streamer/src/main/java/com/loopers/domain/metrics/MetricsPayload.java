package com.loopers.domain.metrics;

import java.util.List;

/**
 * 상품 지표 이벤트의 페이로드.
 *
 * <p>Consumer에서 Kafka 메시지를 파싱하여 생성하고,
 * Handler에서 타입별로 캐스팅하여 사용한다.</p>
 */
public sealed interface MetricsPayload permits MetricsPayload.Like, MetricsPayload.Order, MetricsPayload.View {

    record Like(Long productId, boolean liked) implements MetricsPayload {
    }

    record Order(List<OrderItem> orderItems) implements MetricsPayload {
        public record OrderItem(Long productId, Long quantity) {
        }
    }

    record View(Long productId) implements MetricsPayload {
    }
}

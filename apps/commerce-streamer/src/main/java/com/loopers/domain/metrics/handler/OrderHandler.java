package com.loopers.domain.metrics.handler;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.MetricsEventHandler;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문 완료 이벤트 처리 핸들러.
 *
 * <p>{@link MetricsEventType#ORDER_COMPLETED}를 처리하여
 * 주문 항목별로 상품 판매량을 누적한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OrderHandler implements MetricsEventHandler {

    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(MetricsEventType eventType) {
        return eventType == MetricsEventType.ORDER_COMPLETED;
    }

    @Override
    public void handle(MetricsPayload payload) {
        MetricsPayload.Order order = (MetricsPayload.Order) payload;

        for (MetricsPayload.Order.OrderItem item : order.orderItems()) {
            productMetricsRepository.upsertOrderCount(item.productId(), item.quantity());
        }
    }
}

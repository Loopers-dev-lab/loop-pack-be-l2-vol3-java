package com.loopers.domain.metrics.handler;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.MetricsEventHandler;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 조회 이벤트 처리 핸들러.
 *
 * <p>{@link MetricsEventType#PRODUCT_VIEWED}를 처리하여
 * 상품별 조회 수를 증가시킨다.</p>
 */
@Component
@RequiredArgsConstructor
public class ViewHandler implements MetricsEventHandler {

    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(MetricsEventType eventType) {
        return eventType == MetricsEventType.PRODUCT_VIEWED;
    }

    @Override
    public void handle(MetricsPayload payload) {
        MetricsPayload.View view = (MetricsPayload.View) payload;
        productMetricsRepository.upsertViewCount(view.productId());
    }
}

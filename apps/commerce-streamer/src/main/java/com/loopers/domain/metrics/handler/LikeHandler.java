package com.loopers.domain.metrics.handler;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.MetricsEventHandler;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요 이벤트 처리 핸들러.
 *
 * <p>{@link MetricsEventType#LIKED}와 {@link MetricsEventType#UNLIKED}를 처리하여
 * 상품별 좋아요 수를 증감한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LikeHandler implements MetricsEventHandler {

    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(MetricsEventType eventType) {
        return eventType == MetricsEventType.LIKED || eventType == MetricsEventType.UNLIKED;
    }

    @Override
    public void handle(MetricsPayload payload) {
        MetricsPayload.Like like = (MetricsPayload.Like) payload;
        long delta = like.liked() ? 1L : -1L;
        productMetricsRepository.upsertLikeCount(like.productId(), delta);
    }
}

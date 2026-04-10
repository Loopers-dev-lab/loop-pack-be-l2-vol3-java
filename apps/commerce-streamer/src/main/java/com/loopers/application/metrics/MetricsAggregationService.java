package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandledEntity;
import com.loopers.domain.metrics.ProductMetricsEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsAggregationService {

    private final ProductMetricsJpaRepository metricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final RankingRedisUpdater rankingRedisUpdater;

    @Transactional
    public void incrementViewCount(String eventId, Long productId) {
        if (isAlreadyHandled(eventId)) return;

        ProductMetricsEntity metrics = metricsRepository.findByProductId(productId)
                .map(entity -> {
                    entity.incrementViewCount();
                    return entity;
                })
                .orElseGet(() -> metricsRepository.save(
                        ProductMetricsEntity.createWithView(productId)));

        rankingRedisUpdater.incrementViewCount(productId);
        markHandled(eventId);
        log.info("[Metrics] 조회수 +1 — productId={}, viewCount={}", productId, metrics.getViewCount());
    }

    @Transactional
    public void addLikeCount(String eventId, Long productId, long delta) {
        if (isAlreadyHandled(eventId)) return;

        ProductMetricsEntity metrics = metricsRepository.findByProductId(productId)
                .map(entity -> {
                    entity.addLikeCount(delta);
                    return entity;
                })
                .orElseGet(() -> metricsRepository.save(
                        ProductMetricsEntity.createWithLike(productId, delta)));

        rankingRedisUpdater.incrementLikeCount(productId, delta);
        markHandled(eventId);
        log.info("[Metrics] 좋아요 {} — productId={}, likeCount={}",
                delta > 0 ? "+" + delta : delta, productId, metrics.getLikeCount());
    }

    @Transactional
    public void addSalesCount(String eventId, Long orderId) {
        if (isAlreadyHandled(eventId)) return;

        // TODO: ORDER_COMPLETED 이벤트에 productId별 데이터 추가 후 ZINCRBY 적용
        // 현재 이벤트에는 orderId/totalPrice만 존재하여 상품별 랭킹 점수 반영 불가
        markHandled(eventId);
        log.info("[Metrics] 주문 완료 집계 — orderId={}", orderId);
    }

    private boolean isAlreadyHandled(String eventId) {
        if (eventHandledRepository.existsById(eventId)) {
            log.info("[Metrics] 이미 처리된 이벤트 스킵 — eventId={}", eventId);
            return true;
        }
        return false;
    }

    private void markHandled(String eventId) {
        eventHandledRepository.save(new EventHandledEntity(eventId));
    }
}

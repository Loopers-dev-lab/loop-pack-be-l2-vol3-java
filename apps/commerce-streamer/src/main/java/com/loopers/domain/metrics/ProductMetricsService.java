package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 지표 서비스.
 *
 * <p>Kafka Consumer로부터 호출되어 상품 지표를 upsert 갱신한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    public void incrementViewCount(Long productId) {
        productMetricsRepository.incrementViewCount(productId);
    }

    public void incrementViewCountBy(Long productId, int count) {
        productMetricsRepository.incrementViewCountBy(productId, count);
    }

    public void incrementLikeCount(Long productId) {
        productMetricsRepository.incrementLikeCount(productId);
    }

    public void decrementLikeCount(Long productId) {
        productMetricsRepository.decrementLikeCount(productId);
    }

    /**
     * 좋아요 수를 스냅샷 값으로 upsert한다 (배치 집계용).
     *
     * @param productId 상품 ID
     * @param likeCount 최신 좋아요 수
     * @param occurredAt 이벤트 발생 시각 (로깅용)
     */
    public void upsertLikeCount(Long productId, long likeCount, java.time.LocalDateTime occurredAt) {
        productMetricsRepository.forceUpdateLikeCount(productId, likeCount);
        log.debug("[Metrics] upsertLikeCount productId={}, likeCount={}, occurredAt={}", productId, likeCount, occurredAt);
    }

    public void incrementOrderCount(Long productId, long amount) {
        productMetricsRepository.incrementOrderCount(productId, amount);
    }

    @Transactional(readOnly = true)
    public List<ProductMetricsModel> findAll() {
        return productMetricsRepository.findAll();
    }
}

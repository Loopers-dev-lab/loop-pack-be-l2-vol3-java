package com.loopers.application.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 지표(product_metrics) 갱신을 담당하는 서비스.
 *
 * <p>해당 상품의 ProductMetrics가 존재하지 않으면 새로 생성한 뒤 갱신한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    /**
     * 좋아요 수를 1 증가시킨다.
     *
     * @param productId 대상 상품 ID
     */
    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductMetrics metrics = getOrCreate(productId);
        metrics.incrementLikeCount();
    }

    /**
     * 좋아요 수를 1 감소시킨다. 0 미만으로 내려가지 않는다.
     *
     * @param productId 대상 상품 ID
     */
    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductMetrics metrics = getOrCreate(productId);
        metrics.decrementLikeCount();
    }

    /**
     * 판매량에 주문 수량을 누적한다.
     *
     * @param productId 대상 상품 ID
     * @param quantity  추가할 주문 수량
     */
    @Transactional
    public void addOrderCount(Long productId, Long quantity) {
        ProductMetrics metrics = getOrCreate(productId);
        metrics.addOrderCount(quantity);
    }

    /**
     * 조회 수를 1 증가시킨다.
     *
     * @param productId 대상 상품 ID
     */
    @Transactional
    public void incrementViewCount(Long productId) {
        ProductMetrics metrics = getOrCreate(productId);
        metrics.incrementViewCount();
    }

    private ProductMetrics getOrCreate(Long productId) {
        return productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetrics.create(productId)));
    }
}

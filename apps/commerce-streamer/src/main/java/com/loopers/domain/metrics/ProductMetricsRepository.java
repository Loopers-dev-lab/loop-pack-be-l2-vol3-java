package com.loopers.domain.metrics;

import java.util.List;
import java.util.Optional;

/**
 * 상품 지표 리포지토리 인터페이스.
 */
public interface ProductMetricsRepository {

    Optional<ProductMetricsModel> findById(Long productId);

    ProductMetricsModel save(ProductMetricsModel model);

    void incrementViewCount(Long productId);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);

    void incrementOrderCount(Long productId, long amount);

    List<ProductMetricsModel> findAll();

    /**
     * product_metrics.like_count와 products.like_count가 불일치하는 상품 목록을 조회한다.
     *
     * @return 불일치 감지 결과 목록
     */
    List<MetricsLikeCountMismatch> findLikeCountMismatches();

    /**
     * 상품 지표의 like_count를 강제 갱신한다.
     *
     * @param productId 상품 ID
     * @param likeCount 갱신할 좋아요 수
     */
    void forceUpdateLikeCount(Long productId, long likeCount);
}

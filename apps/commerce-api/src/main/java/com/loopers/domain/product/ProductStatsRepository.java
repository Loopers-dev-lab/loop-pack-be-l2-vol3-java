package com.loopers.domain.product;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 상품 집계(product_stats) 영속성 인터페이스.
 */
public interface ProductStatsRepository {

    /**
     * 상품 ID에 대한 행이 없으면 like_count=0으로 생성한다.
     */
    void createIfAbsent(Long productId);

    /**
     * like_count를 1 증가시킨다. (아토믹 업데이트)
     */
    void incrementLikeCount(Long productId);

    /**
     * like_count를 1 감소시킨다. 0 미만이 되지 않는다. (아토믹 업데이트)
     */
    void decrementLikeCount(Long productId);

    /**
     * 단일 상품의 집계 행을 삭제한다. (상품 삭제 시 정리용)
     */
    void deleteByProductId(Long productId);

    Optional<ProductStatsModel> findByProductId(Long productId);

    /**
     * 상품 ID 목록별 like_count를 반환한다. (PLP/PDP 읽기 경로용, product_stats 단일 조회)
     * 목록에 없거나 행이 없는 상품은 map에 포함되지 않으며, 호출부에서 getOrDefault(id, 0L) 사용.
     */
    Map<Long, Long> findLikeCountByProductIds(Collection<Long> productIds);
}

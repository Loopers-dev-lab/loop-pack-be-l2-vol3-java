package com.loopers.domain.metrics;

/**
 * 상품 지표 저장소.
 *
 * <p>상품별 좋아요 수, 판매량, 조회 수를 upsert 방식으로 갱신한다.
 * 대상 상품의 지표가 존재하지 않으면 새로 생성하고, 존재하면 갱신한다.</p>
 */
public interface ProductMetricsRepository {

    /**
     * 상품의 좋아요 수를 delta만큼 증감한다. 0 미만으로 내려가지 않는다.
     *
     * @param productId 대상 상품 ID
     * @param delta     증감량 (좋아요: +1, 취소: -1)
     */
    void upsertLikeCount(Long productId, Long delta);

    /**
     * 상품의 판매량에 수량을 누적한다.
     *
     * @param productId 대상 상품 ID
     * @param quantity  추가할 주문 수량
     */
    void upsertOrderCount(Long productId, Long quantity);

    /**
     * 상품의 조회 수를 1 증가시킨다.
     *
     * @param productId 대상 상품 ID
     */
    void upsertViewCount(Long productId);
}

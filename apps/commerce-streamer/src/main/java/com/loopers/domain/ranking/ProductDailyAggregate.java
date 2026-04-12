package com.loopers.domain.ranking;

import java.math.BigDecimal;

/**
 * 특정 상품에 대해 "해당 일자" 범위로 합산된 집계 스냅샷 VO.
 *
 * Native 집계 쿼리의 결과를 그대로 받아 점수 계산의 입력으로 사용된다.
 * 필드는 {@link RankingScoreCalculator#calculate(ProductDailyAggregate)} 의 입력이다.
 *
 * null-safety:
 * - {@code totalOrderAmount} 는 Native SQL {@code SUM(order_amount)} 가 매칭 row 없을 때
 *   NULL 을 반환할 수 있으므로 호출 측에서 {@code BigDecimal.ZERO} 로 보정.
 */
public record ProductDailyAggregate(
        Long productId,
        long totalView,
        long totalLike,
        long totalOrder,
        BigDecimal totalOrderAmount
) {

    public static ProductDailyAggregate empty(Long productId) {
        return new ProductDailyAggregate(productId, 0L, 0L, 0L, BigDecimal.ZERO);
    }
}

package com.loopers.domain.ranking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 랭킹 점수 계산기.
 *
 * <p>이벤트 타입별 가중치(weight)와 rawScore를 곱하여 최종 점수를 반환한다.
 * 주문 이벤트는 {@code log10(price × quantity)}로 금액을 압축하여 조회/좋아요의 영향력을 유지한다.</p>
 *
 */
@Component
public class RankingScoreCalculator {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    /**
     * 조회 점수를 반환한다.
     */
    public double calculateViewScore() {
        return VIEW_WEIGHT;
    }

    /**
     * 좋아요 점수를 반환한다.
     *
     * @param liked true이면 양수, false(취소)이면 음수
     */
    public double calculateLikeScore(boolean liked) {
        return liked ? LIKE_WEIGHT : -LIKE_WEIGHT;
    }

    /**
     * 주문 항목별 점수를 계산하여 상품 ID별로 합산한다.
     *
     * @param orderItems 주문 항목 목록
     * @return 상품 ID → 합산 점수
     */
    public Map<Long, Double> calculateOrderScores(List<RankingEvent.Order.OrderItem> orderItems) {
        Map<Long, Double> scores = new HashMap<>();
        for (RankingEvent.Order.OrderItem item : orderItems) {
            scores.merge(item.productId(), calculateOrderScore(item.price(), item.quantity()), Double::sum);
        }
        return scores;
    }

    /**
     * 주문 점수를 반환한다.
     *
     * <p>price 또는 quantity가 0 이하이면 0.0을 반환한다.</p>
     *
     * @param price    상품 단가 (원)
     * @param quantity 주문 수량
     */
    private double calculateOrderScore(long price, long quantity) {
        if (price <= 0 || quantity <= 0) {
            return 0.0;
        }
        return ORDER_WEIGHT * Math.log10(price * quantity);
    }
}

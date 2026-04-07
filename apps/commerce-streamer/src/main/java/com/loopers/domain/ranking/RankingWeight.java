package com.loopers.domain.ranking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 랭킹 점수 가중치 상수.
 *
 * <p>업계 랭킹 신호 비율 기반 설계:</p>
 * <ul>
 *   <li>발견 신호(조회): ~10% 기여</li>
 *   <li>관여 신호(좋아요): ~25% 기여</li>
 *   <li>구매 신호(주문): ~65% 기여</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingWeight {

    /** 조회 1건당 점수. 빈도 최다이므로 가장 낮게 설정. */
    public static final double VIEW = 0.01;

    /** 좋아요 1건당 점수. SET 멱등 처리와 함께 사용. */
    public static final double LIKE = 0.3;

    /** 주문 건수 기본 점수 (금액 보너스 전). */
    public static final double ORDER_BASE = 1.0;

    /** 주문 금액 보너스 스케일링 팩터. score = ORDER_BASE + PRICE_EPSILON × log₁₀(amount + 1) */
    public static final double PRICE_EPSILON = 0.01;

    /**
     * 주문 1건의 점수를 계산한다. (건수 우선 + 금액 보너스)
     *
     * @param amount 주문 금액 (price × quantity)
     * @return ORDER_BASE + PRICE_EPSILON × log₁₀(amount + 1)
     */
    public static double orderScore(long amount) {
        return ORDER_BASE + PRICE_EPSILON * Math.log10(amount + 1);
    }
}

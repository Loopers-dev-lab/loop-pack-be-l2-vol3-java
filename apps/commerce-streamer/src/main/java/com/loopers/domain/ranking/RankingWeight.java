package com.loopers.domain.ranking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 랭킹 점수 가중치 상수.
 *
 * <p>총합 1.0 제약 하의 가중치 설계 (view 0.1 + like 0.2 + order 0.7 = 1.0).
 * 주문 > 좋아요 > 조회 순서를 보장하되, 대량의 좋아요/조회가 소수의 주문을
 * 역전할 수 있는 소프트 계층(Soft Hierarchy) 구조.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingWeight {

    /** 조회 1건당 점수. 빈도 최다이므로 가장 낮게 설정. */
    public static final double VIEW = 0.1;

    /** 좋아요 1건당 점수. SET 멱등 처리와 함께 사용. */
    public static final double LIKE = 0.2;

    /** 주문 건수 기본 점수 (금액 보너스 전). */
    public static final double ORDER_BASE = 0.7;

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

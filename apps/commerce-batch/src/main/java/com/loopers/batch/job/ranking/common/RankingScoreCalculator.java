package com.loopers.batch.job.ranking.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 가중치 기반 랭킹 점수 계산기.
 *
 * <p>streamer의 {@code RankingWeight.orderScore(amount)}와 같은 방향성을 갖도록
 * 주문 건수에 <b>금액 log 보너스</b>를 추가로 합산한다. streamer는 주문별로
 * {@code 0.7 + 0.01·log₁₀(amount+1)}를 실시간 누적하지만, Batch는 일별 테이블에서
 * 개별 주문 금액을 잃었으므로 <b>평균 금액 기반 근사</b>를 사용한다.</p>
 *
 * <p><b>근사 식</b>:
 * {@code score = Σ(like·W_like + order·(W_order + ε·log₁₀(avgAmount+1)) + view·W_view)}
 * 여기서 {@code avgAmount = orderAmount / orderCount}.</p>
 *
 * <p><b>편향 주의</b>: 젠센 부등식에 의해 {@code log(avg) ≥ avg(log)}이므로
 * 본 근사는 streamer의 실제 점수보다 <b>과대평가</b>된다. 하지만 "금액 큰 주문이
 * 많을수록 점수 높음"이라는 방향성은 일치하므로 DAILY와 WEEKLY/MONTHLY의
 * <b>상대 순위</b>는 유사하게 유지된다.</p>
 */
@Component
@RequiredArgsConstructor
public class RankingScoreCalculator {

    private static final int SCALE = 4;
    private static final double PRICE_EPSILON = 0.01;

    private final RankingWeights weights;

    public BigDecimal calculate(long likeCount, long orderCount, long viewCount, long orderAmount) {
        double avgAmount = orderCount > 0 ? (double) orderAmount / orderCount : 0.0;
        double orderScorePerItem = weights.getOrder() + PRICE_EPSILON * Math.log10(avgAmount + 1);

        double score = likeCount * weights.getLike()
                + orderCount * orderScorePerItem
                + viewCount * weights.getView();

        return BigDecimal.valueOf(score).setScale(SCALE, RoundingMode.HALF_UP);
    }
}

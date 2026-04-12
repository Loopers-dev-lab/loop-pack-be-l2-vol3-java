package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 랭킹 점수 계산기.
 *
 * <p>공식 (전체 log1p 정규화):
 * <pre>
 *     score = w.view  * log1p(totalView)
 *           + w.like  * log1p(totalLike)
 *           + w.order * log1p(totalOrderAmount)
 * </pre>
 *
 * <p>정규화는 "일간 총합을 한 번만" 적용한다 — {@code Σ log1p ≠ log1p(Σ)} 비선형 함정을 피하기 위함.
 *
 * <p>null-safety:
 * {@code totalOrderAmount} 가 null 인 경우 0 으로 clamp 한다 (CLAUDE.md "null-safety 강제" 준수).
 *
 * <p>정밀도:
 * {@code totalOrderAmount} 는 {@code BigDecimal} 에서 {@code double} 로 변환되어 {@code log1p} 입력으로 쓰인다.
 * 일간 합계가 매우 큰 금액 단위(수십억 원) 가 되어도 {@code log1p} 의 출력 차이는 무시 가능한 수준이며,
 * 점수 비교의 상대적 순서에는 영향을 주지 않는다 (모두 같은 비선형 변환을 거치므로).
 */
@Component
@RequiredArgsConstructor
public class RankingScoreCalculator {

    private final RankingWeights weights;

    public double calculate(ProductDailyAggregate agg) {
        if (agg == null) {
            return 0.0;
        }
        double amount = agg.totalOrderAmount() == null
                ? 0.0
                : Math.max(agg.totalOrderAmount().doubleValue(), 0.0);
        double view = Math.max(agg.totalView(), 0L);
        double like = Math.max(agg.totalLike(), 0L);

        return weights.view() * Math.log1p(view)
                + weights.like() * Math.log1p(like)
                + weights.order() * Math.log1p(amount);
    }

    public double calculateRaw(long view, long like, BigDecimal orderAmount) {
        return calculate(new ProductDailyAggregate(
                null,
                Math.max(view, 0L),
                Math.max(like, 0L),
                0L,
                orderAmount == null ? BigDecimal.ZERO : orderAmount
        ));
    }
}

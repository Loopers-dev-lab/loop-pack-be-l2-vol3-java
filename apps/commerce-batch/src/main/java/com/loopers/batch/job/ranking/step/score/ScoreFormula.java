package com.loopers.batch.job.ranking.step.score;

import com.loopers.domain.ranking.weight.WeightConfig;

/**
 * 랭킹 스코어 공식 — 순수 함수.
 * {@code score = w_view × viewCount + w_like × likeCount + w_order × log10(salesAmount + 1)}
 *
 * <p>sales_amount 는 금액 단위라 view/like 카운트에 비해 스케일이 크므로 log10 으로 정규화한다
 * (설계 히스토리: "주문 스코어링에 log10(salesAmount) 정규화 적용" 커밋).</p>
 *
 * <p>변환 로직은 이 한 곳에 격리되어 있어 가중치·수식 변경 시 이 클래스만 수정하면 된다
 * (설계.md "변경이 자주 있을 부분은 갈아끼울 수 있게" 원칙).</p>
 */
public final class ScoreFormula {

    private ScoreFormula() {
    }

    public static double compute(long viewCount, long likeCount, long salesAmount, WeightConfig config) {
        return config.getWView()  * viewCount
             + config.getWLike()  * likeCount
             + config.getWOrder() * Math.log10(salesAmount + 1.0);
    }
}

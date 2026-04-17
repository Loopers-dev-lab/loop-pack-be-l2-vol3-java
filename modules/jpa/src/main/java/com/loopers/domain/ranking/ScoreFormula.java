package com.loopers.domain.ranking;

/**
 * 랭킹 Score 공식 — Single Source of Truth.
 *
 * <p>수식 (v2 — 0~1 정규화):
 * {@code categoryPriority + W(view)×log₁₀(viewCount+1)/MAX_LOG
 *   + W(like)×log₁₀(likeCount+1)/MAX_LOG
 *   + W(order)×log₁₀(salesAmount+1)/MAX_LOG
 *   + lastEventEpochSeconds × TIEBREAKER_SCALE}</p>
 *
 * <p>commerce-streamer(RankingScoreUpdater), commerce-batch(RankingCorrectionJobConfig,
 * ProductRankingMvJobConfig) 세 곳에서 이 클래스에 위임한다.</p>
 */
public final class ScoreFormula {

    /**
     * MAX_LOG = 7 → log₁₀(10,000,000).
     * 쿠팡급 인기 상품의 일일 최대 메트릭(조회 수백만, 매출 수천만)을 0~1로 정규화.
     */
    public static final double MAX_LOG = 7.0;

    /**
     * Tiebreaker: lastEventEpochSeconds × 1e-16.
     * epoch seconds ≈ 1.7×10⁹ → tiebreaker ≈ 1.7×10⁻⁷.
     * 주 score 최소 차이(0.1×log₁₀(2)/7 ≈ 0.0043)보다 충분히 작아 역전 불가.
     */
    public static final double TIEBREAKER_SCALE = 1e-16;

    private ScoreFormula() {}

    public static double calculate(
        long viewCount, long netLikeCount, long netSalesAmount,
        int categoryPriority, long lastEventEpochSeconds,
        Weights w
    ) {
        return categoryPriority
            + w.view() * Math.log10(Math.max(0, viewCount) + 1) / MAX_LOG
            + w.like() * Math.log10(Math.max(0, netLikeCount) + 1) / MAX_LOG
            + w.order() * Math.log10(Math.max(0, netSalesAmount) + 1) / MAX_LOG
            + lastEventEpochSeconds * TIEBREAKER_SCALE;
    }

    public record Weights(double view, double like, double order) {}
}

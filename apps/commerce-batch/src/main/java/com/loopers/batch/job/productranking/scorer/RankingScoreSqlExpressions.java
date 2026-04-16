package com.loopers.batch.job.productranking.scorer;


/**
 * 랭킹 Scorer SQL 표현식 (SATURATION 기본값)
 * - Java scorer(RankingScorerConfig)와 동일한 공식을 SQL로 표현
 * - Batch Reader SQL에서 score 계산 + ROW_NUMBER() 정렬에 사용
 * - scorer 추가 시 반드시 Java scorer와 SQL expression을 함께 추가
 *
 * SATURATION 공식:
 *   score = 0.15 * sat(view, 100) + 0.35 * sat(like, 10) + 0.50 * sat(sales, 3)
 *   sat(x, k) = x / (x + k)  단 x <= 0이면 0
 */
public final class RankingScoreSqlExpressions {

	// SATURATION scorer SQL 표현식
	public static final String SATURATION_SCORE_SQL =
		"0.15 * CASE WHEN view_count <= 0 THEN 0 ELSE view_count / (view_count + 100.0) END " +
		"+ 0.35 * CASE WHEN like_count <= 0 THEN 0 ELSE like_count / (like_count + 10.0) END " +
		"+ 0.50 * CASE WHEN sales_count <= 0 THEN 0 ELSE sales_count / (sales_count + 3.0) END";

	// 지원 scorer 타입
	public static final String SATURATION = "SATURATION";


	private RankingScoreSqlExpressions() {
	}


	/**
	 * scorerType에 해당하는 SQL score 표현식을 반환한다.
	 * 지원하지 않는 타입이면 IllegalArgumentException으로 fail-fast 처리한다.
	 */
	public static String resolveScoreExpression(String scorerType) {
		if (SATURATION.equals(scorerType)) {
			return SATURATION_SCORE_SQL;
		}
		throw new IllegalArgumentException("지원하지 않는 scorerType: " + scorerType + ". 허용값: " + SATURATION);
	}

}

package com.loopers.domain.ranking;

/**
 * 랭킹 점수 계산기.
 * 공식: view × VIEW_WEIGHT + like × LIKE_WEIGHT + order × ORDER_WEIGHT
 * SQL 집계(Weekly/Monthly Reader)와 동일한 가중치. 공식 변경은 본 클래스에서만.
 */
public final class RankingScoreCalculator {

    public static final int VIEW_WEIGHT  = 1;
    public static final int LIKE_WEIGHT  = 2;
    public static final int ORDER_WEIGHT = 7;

    private RankingScoreCalculator() {
    }

    public static double calculate(long viewCount, long likeCount, long orderCount) {
        return viewCount * VIEW_WEIGHT
             + likeCount * LIKE_WEIGHT
             + orderCount * ORDER_WEIGHT;
    }

    /**
     * SQL에서 계산된 점수와 Calculator 공식이 drift되지 않았는지 검증한다.
     * Reader SQL의 가중치가 Calculator 상수와 어긋나면 즉시 실패시켜 잘못된 랭킹 적재를 막는다.
     */
    public static void assertConsistent(double scoreFromSql, long viewCount, long likeCount, long orderCount) {
        double expected = calculate(viewCount, likeCount, orderCount);
        if (scoreFromSql != expected) {
            throw new IllegalStateException(
                "Ranking score drift detected: SQL=%s Calculator=%s (view=%d like=%d order=%d)"
                    .formatted(scoreFromSql, expected, viewCount, likeCount, orderCount)
            );
        }
    }
}

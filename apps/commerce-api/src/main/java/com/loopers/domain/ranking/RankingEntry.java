package com.loopers.domain.ranking;

/**
 * 랭킹 ZSET 엔트리 VO — (productId, rank(1-based), score).
 *
 * 순위 번호는 1부터 시작한다 (사용자 노출 관례).
 */
public record RankingEntry(
        Long productId,
        long rank,
        double score
) {
}

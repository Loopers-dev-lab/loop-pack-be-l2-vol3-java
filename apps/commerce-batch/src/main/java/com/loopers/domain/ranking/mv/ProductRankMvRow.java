package com.loopers.domain.ranking.mv;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 주간/월간 랭킹 MV 조회·적재용 불변 행.
 */
public record ProductRankMvRow(
        Optional<Long> id,
        String periodKey,
        long productId,
        int rank,
        BigDecimal score,
        int version,
        Instant updatedAt
) {

    /**
     * 새로운 행을 생성한다.
     *
     * @param periodKey 기간 키
     * @param productId 상품 ID
     * @param rank 랭킹
     * @param score 랭킹 점수
     * @param version 버전
     * @param updatedAt 업데이트 시간
     */
    public static ProductRankMvRow newRow(
            String periodKey,
            long productId,
            int rank,
            BigDecimal score,
            int version,
            Instant updatedAt
    ) {
        return new ProductRankMvRow(Optional.empty(), periodKey, productId, rank, score, version, updatedAt);
    }
}

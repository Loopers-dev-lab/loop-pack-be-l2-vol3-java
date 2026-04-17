package com.loopers.domain.ranking.batch;

import java.math.BigDecimal;

/**
 * 스테이징 테이블에 저장할 순위 확정 행.
 */
public record RankingStagingRankRow(int rank, long productId, BigDecimal score) {
}

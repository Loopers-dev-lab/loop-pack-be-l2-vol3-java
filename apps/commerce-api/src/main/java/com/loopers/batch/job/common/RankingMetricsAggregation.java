package com.loopers.batch.job.common;

import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 랭킹 메트릭 집계 결과 DTO.
 * 주간/월간 랭킹 집계 Job에서 공통으로 사용합니다.
 */
public record RankingMetricsAggregation(
    Long productId,
    long totalViewCount,
    long totalLikeCount,
    long totalOrderCount,
    BigDecimal totalScore
) {
    /**
     * ResultSet을 RankingMetricsAggregation으로 변환하는 RowMapper.
     */
    public static class RankingMetricsRowMapper implements RowMapper<RankingMetricsAggregation> {
        @Override
        public RankingMetricsAggregation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RankingMetricsAggregation(
                rs.getLong("product_id"),
                rs.getLong("total_view_count"),
                rs.getLong("total_like_count"),
                rs.getLong("total_order_count"),
                rs.getBigDecimal("total_score")
            );
        }
    }
}
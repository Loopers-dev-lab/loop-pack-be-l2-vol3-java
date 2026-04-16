package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WeeklyRankingRepositoryImpl implements WeeklyRankingRepository {

    private static final String SELECT_SQL =
        "SELECT product_id, score, ranking_position "
            + "FROM mv_product_rank_weekly "
            + "WHERE year_week = ? "
            + "ORDER BY ranking_position "
            + "LIMIT ? OFFSET ?";

    private static final String COUNT_SQL =
        "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE year_week = ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ProductRanking> findTopN(String periodKey, long offset, int limit) {
        return jdbcTemplate.query(
            SELECT_SQL,
            (rs, rowNum) -> new ProductRanking(
                rs.getLong("product_id"),
                rs.getDouble("score"),
                rs.getLong("ranking_position")
            ),
            periodKey, limit, offset
        );
    }

    @Override
    public long countByPeriod(String periodKey) {
        Long count = jdbcTemplate.queryForObject(COUNT_SQL, Long.class, periodKey);
        return count != null ? count : 0L;
    }
}

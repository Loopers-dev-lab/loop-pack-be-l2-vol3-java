package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvProductRankRepositoryImpl implements MvProductRankRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RankingEntry> findByPeriodKey(RankPeriodType type, String periodKey, long offset, long size) {
        String sql = "SELECT ref_product_id, score FROM " + type.getTableName() +
                " WHERE period_key = ? ORDER BY rank_no ASC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RankingEntry(
                        rs.getLong("ref_product_id"),
                        rs.getDouble("score")
                ),
                periodKey, size, offset);
    }

    @Override
    public long countByPeriodKey(RankPeriodType type, String periodKey) {
        String sql = "SELECT COUNT(*) FROM " + type.getTableName() + " WHERE period_key = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, periodKey);
        return count != null ? count : 0L;
    }
}

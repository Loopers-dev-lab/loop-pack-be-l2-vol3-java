package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<ZonedDateTime> findLastUpdatedAt(RankPeriodType type, String periodKey) {
        String sql = "SELECT MAX(updated_at) FROM " + type.getTableName() + " WHERE period_key = ?";
        Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class, periodKey);
        if (ts == null) {
            return Optional.empty();
        }
        return Optional.of(ts.toInstant().atZone(java.time.ZoneId.of("Asia/Seoul")));
    }
}

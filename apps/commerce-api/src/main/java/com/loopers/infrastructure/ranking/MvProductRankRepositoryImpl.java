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
        String sql = "SELECT mv.ref_product_id, mv.score FROM " + type.getTableName() + " mv " +
                "INNER JOIN mv_product_rank_publication p " +
                "ON p.period_type = ? AND p.period_key = mv.period_key AND p.published_version = mv.version " +
                "WHERE mv.period_key = ? ORDER BY mv.rank_no ASC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RankingEntry(
                        rs.getLong("ref_product_id"),
                        rs.getDouble("score")
                ),
                type.name(), periodKey, size, offset);
    }

    @Override
    public long countByPeriodKey(RankPeriodType type, String periodKey) {
        String sql = "SELECT COUNT(*) FROM " + type.getTableName() + " mv " +
                "INNER JOIN mv_product_rank_publication p " +
                "ON p.period_type = ? AND p.period_key = mv.period_key AND p.published_version = mv.version " +
                "WHERE mv.period_key = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, type.name(), periodKey);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<Long> findPublishedVersion(RankPeriodType type, String periodKey) {
        List<Long> result = jdbcTemplate.queryForList(
                "SELECT published_version FROM mv_product_rank_publication " +
                        "WHERE period_type = ? AND period_key = ?",
                Long.class, type.name(), periodKey
        );
        return result.isEmpty() ? Optional.empty() : Optional.ofNullable(result.get(0));
    }

    @Override
    public Optional<ZonedDateTime> findLastUpdatedAt(RankPeriodType type, String periodKey) {
        String sql = "SELECT MAX(mv.updated_at) FROM " + type.getTableName() + " mv " +
                "INNER JOIN mv_product_rank_publication p " +
                "ON p.period_type = ? AND p.period_key = mv.period_key AND p.published_version = mv.version " +
                "WHERE mv.period_key = ?";
        Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class, type.name(), periodKey);
        if (ts == null) {
            return Optional.empty();
        }
        return Optional.of(ts.toInstant().atZone(java.time.ZoneId.of("Asia/Seoul")));
    }
}

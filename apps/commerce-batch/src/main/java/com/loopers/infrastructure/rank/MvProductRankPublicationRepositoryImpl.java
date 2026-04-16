package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.MvProductRankPublicationRepository;
import com.loopers.domain.rank.RankPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MvProductRankPublicationRepositoryImpl implements MvProductRankPublicationRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public long bumpNextVersion(RankPeriodType type, String periodKey) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                        "VALUES (?, ?, 1, 0, NOW(6)) " +
                        "ON DUPLICATE KEY UPDATE next_version = next_version + 1, updated_at = NOW(6)",
                type.name(), periodKey
        );
        Long version = jdbcTemplate.queryForObject(
                "SELECT next_version FROM mv_product_rank_publication WHERE period_type = ? AND period_key = ?",
                Long.class, type.name(), periodKey
        );
        if (version == null) {
            throw new IllegalStateException("publication row 누락: type=" + type + " periodKey=" + periodKey);
        }
        return version;
    }

    @Override
    public long findPublishedVersion(RankPeriodType type, String periodKey) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT published_version FROM mv_product_rank_publication WHERE period_type = ? AND period_key = ?",
                Long.class, type.name(), periodKey
        );
        return v == null ? 0L : v;
    }

    @Override
    public boolean casPublishIfGreater(RankPeriodType type, String periodKey, long newVersion) {
        int updated = jdbcTemplate.update(
                "UPDATE mv_product_rank_publication SET published_version = ?, updated_at = NOW(6) " +
                        "WHERE period_type = ? AND period_key = ? AND published_version < ?",
                newVersion, type.name(), periodKey, newVersion
        );
        return updated > 0;
    }
}

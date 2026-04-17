package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.MvProductRankWeekly;
import com.loopers.domain.rank.MvProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MvProductRankWeeklyRepositoryImpl implements MvProductRankWeeklyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // created_at 은 ON DUPLICATE KEY UPDATE 절에 포함하지 않아 최초 INSERT 시각 보존
    private static final String UPSERT_SQL = """
        INSERT INTO mv_product_rank_weekly
            (snapshot_date, product_id, rank_position, score, view_count, like_count, order_revenue, created_at)
        VALUES
            (:snapshotDate, :productId, :rank, :score, :viewCount, :likeCount, :orderRevenue, NOW(6))
        ON DUPLICATE KEY UPDATE
            rank_position  = VALUES(rank_position),
            score          = VALUES(score),
            view_count     = VALUES(view_count),
            like_count     = VALUES(like_count),
            order_revenue  = VALUES(order_revenue)
        """;

    @Override
    public void upsertAll(List<? extends MvProductRankWeekly> rows) {
        SqlParameterSource[] params = rows.stream()
            .map(r -> new MapSqlParameterSource()
                .addValue("snapshotDate", r.getSnapshotDate())
                .addValue("productId", r.getProductId())
                .addValue("rank", r.getRank())
                .addValue("score", r.getScore())
                .addValue("viewCount", r.getViewCount())
                .addValue("likeCount", r.getLikeCount())
                .addValue("orderRevenue", r.getOrderRevenue()))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_SQL, params);
    }

    @Override
    public long countBySnapshotDate(LocalDate snapshotDate) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE snapshot_date = :snapshotDate",
            Map.of("snapshotDate", snapshotDate),
            Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<MvProductRankWeekly> findBySnapshotDateAndProductId(LocalDate snapshotDate, Long productId) {
        List<MvProductRankWeekly> results = jdbc.query(
            "SELECT * FROM mv_product_rank_weekly WHERE snapshot_date = :snapshotDate AND product_id = :productId",
            Map.of("snapshotDate", snapshotDate, "productId", productId),
            (rs, rowNum) -> MvProductRankWeekly.reconstruct(
                rs.getLong("id"),
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getLong("product_id"),
                rs.getInt("rank_position"),
                rs.getDouble("score"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getBigDecimal("order_revenue"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}

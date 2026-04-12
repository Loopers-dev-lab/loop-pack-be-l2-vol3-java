package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvProductRankRepositoryImpl implements MvProductRankRepository {

    private final JdbcTemplate jdbcTemplate;
    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    @Transactional
    public void deleteByPeriodKey(RankPeriodType type, String periodKey) {
        switch (type) {
            case WEEKLY -> weeklyJpaRepository.deleteByPeriodKey(periodKey);
            case MONTHLY -> monthlyJpaRepository.deleteByPeriodKey(periodKey);
        }
    }

    @Override
    public void batchInsert(RankPeriodType type, List<MvProductRankRow> rows) {
        String sql = "INSERT INTO " + type.getTableName() +
                " (period_key, rank_no, ref_product_id, score, view_count, like_count, order_amount, created_at, updated_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(sql, rows, rows.size(),
                (ps, row) -> {
                    ps.setString(1, row.periodKey());
                    ps.setInt(2, row.rankNo());
                    ps.setLong(3, row.refProductId());
                    ps.setDouble(4, row.score());
                    ps.setLong(5, row.viewCount());
                    ps.setLong(6, row.likeCount());
                    ps.setBigDecimal(7, row.orderAmount());
                    ps.setTimestamp(8, now);
                    ps.setTimestamp(9, now);
                });
    }

    @Override
    public List<MvProductRankRow> findByPeriodKey(RankPeriodType type, String periodKey, long offset, long size) {
        String sql = "SELECT period_key, rank_no, ref_product_id, score, view_count, like_count, order_amount" +
                " FROM " + type.getTableName() +
                " WHERE period_key = ? ORDER BY rank_no ASC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new MvProductRankRow(
                        rs.getString("period_key"),
                        rs.getInt("rank_no"),
                        rs.getLong("ref_product_id"),
                        rs.getDouble("score"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getBigDecimal("order_amount")
                ),
                periodKey, size, offset);
    }

    @Override
    public long countByPeriodKey(RankPeriodType type, String periodKey) {
        return switch (type) {
            case WEEKLY -> weeklyJpaRepository.countByPeriodKey(periodKey);
            case MONTHLY -> monthlyJpaRepository.countByPeriodKey(periodKey);
        };
    }
}

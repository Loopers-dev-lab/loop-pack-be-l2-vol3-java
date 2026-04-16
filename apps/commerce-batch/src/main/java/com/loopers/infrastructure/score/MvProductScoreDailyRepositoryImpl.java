package com.loopers.infrastructure.score;

import com.loopers.domain.score.MvProductScoreDailyRepository;
import com.loopers.domain.score.MvProductScoreDailyRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvProductScoreDailyRepositoryImpl implements MvProductScoreDailyRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO mv_product_score_daily
                (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                score = VALUES(score),
                view_count = VALUES(view_count),
                like_count = VALUES(like_count),
                order_amount = VALUES(order_amount),
                updated_at = VALUES(updated_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchUpsert(List<MvProductScoreDailyRow> rows) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(),
                (ps, row) -> {
                    ps.setLong(1, row.productDbId());
                    ps.setObject(2, row.scoreDate());
                    ps.setDouble(3, row.score());
                    ps.setLong(4, row.viewCount());
                    ps.setLong(5, row.likeCount());
                    ps.setBigDecimal(6, row.orderAmount());
                    ps.setTimestamp(7, now);
                    ps.setTimestamp(8, now);
                });
    }
}

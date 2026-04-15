package com.loopers.batch.job.ranking.step.score;

import com.loopers.domain.ranking.staging.StagingRankingScored;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 5 Writer — staging_ranking_scored 에 전체 상품 score 적재.
 * Step 0 가 해당 anchor 의 scored row 를 비워 놓으므로 여기서는 순수 INSERT.
 * 재시작 안전성을 위해 UPSERT 로 기록 (같은 PK 재실행 시 값 갱신).
 */
@Component
@RequiredArgsConstructor
public class StagingScoredWriter implements ItemWriter<List<StagingRankingScored>> {

    private static final String UPSERT_SQL = """
            INSERT INTO staging_ranking_scored
                (period_type, period_key, weight_group, product_id,
                 view_count, like_count, sales_amount, score)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                view_count   = VALUES(view_count),
                like_count   = VALUES(like_count),
                sales_amount = VALUES(sales_amount),
                score        = VALUES(score)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends List<StagingRankingScored>> chunk) {
        List<StagingRankingScored> flattened = new ArrayList<>();
        for (List<StagingRankingScored> group : chunk) {
            flattened.addAll(group);
        }
        if (flattened.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StagingRankingScored row = flattened.get(i);
                ps.setString(1, row.getPeriodType());
                ps.setString(2, row.getPeriodKey());
                ps.setString(3, row.getWeightGroup());
                ps.setLong(4, row.getProductId());
                ps.setLong(5, row.getViewCount());
                ps.setLong(6, row.getLikeCount());
                ps.setLong(7, row.getSalesAmount());
                ps.setDouble(8, row.getScore());
            }

            @Override
            public int getBatchSize() {
                return flattened.size();
            }
        });
    }
}

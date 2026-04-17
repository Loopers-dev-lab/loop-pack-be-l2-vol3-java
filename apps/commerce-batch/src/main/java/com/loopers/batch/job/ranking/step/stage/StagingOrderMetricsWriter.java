package com.loopers.batch.job.ranking.step.stage;

import com.loopers.domain.ranking.staging.StagingRankingAggregation;
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
 * Step 3 Writer — staging_ranking_aggregation 의 sales_amount 만 UPSERT.
 */
@Component
@RequiredArgsConstructor
public class StagingOrderMetricsWriter implements ItemWriter<List<StagingRankingAggregation>> {

    private static final String UPSERT_SQL = """
            INSERT INTO staging_ranking_aggregation
                (period_type, period_key, product_id, view_count, like_count, sales_amount)
            VALUES (?, ?, ?, 0, 0, ?)
            ON DUPLICATE KEY UPDATE
                sales_amount = VALUES(sales_amount)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends List<StagingRankingAggregation>> chunk) {
        List<StagingRankingAggregation> flattened = new ArrayList<>();
        for (List<StagingRankingAggregation> group : chunk) {
            flattened.addAll(group);
        }
        if (flattened.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StagingRankingAggregation row = flattened.get(i);
                ps.setString(1, row.getPeriodType());
                ps.setString(2, row.getPeriodKey());
                ps.setLong(3, row.getProductId());
                ps.setLong(4, row.getSalesAmount());
            }

            @Override
            public int getBatchSize() {
                return flattened.size();
            }
        });
    }
}

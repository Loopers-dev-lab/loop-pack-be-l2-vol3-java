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
 * Step 1 Writer — staging_ranking_aggregation 에 view_count 만 UPSERT.
 *
 * <p>Step 2 (like), Step 3 (order) 는 각각 like_count / sales_amount 만 UPDATE 하므로,
 * Step 1 의 INSERT 이후 해당 row 의 다른 컬럼은 0 으로 남아 있다가 Step 2/3 에서 채워진다.</p>
 *
 * <p>JPA {@code saveAll()} 대신 JDBC batch UPSERT 를 쓰는 이유는 설계.md 의
 * "JPA Writer 함정" 섹션 참고: merge() 건별 SELECT 회피 + 1차 캐시 OOM 방지.</p>
 */
@Component
@RequiredArgsConstructor
public class StagingViewMetricsWriter implements ItemWriter<List<StagingRankingAggregation>> {

    private static final String UPSERT_SQL = """
            INSERT INTO staging_ranking_aggregation
                (period_type, period_key, product_id, view_count, like_count, sales_amount)
            VALUES (?, ?, ?, ?, 0, 0)
            ON DUPLICATE KEY UPDATE
                view_count = VALUES(view_count)
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
                ps.setLong(4, row.getViewCount());
            }

            @Override
            public int getBatchSize() {
                return flattened.size();
            }
        });
    }
}

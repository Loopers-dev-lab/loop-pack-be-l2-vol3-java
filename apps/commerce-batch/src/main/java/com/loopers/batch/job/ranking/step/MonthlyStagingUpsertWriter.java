package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.dto.StagingDelta;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@StepScope
@RequiredArgsConstructor
public class MonthlyStagingUpsertWriter implements ItemWriter<StagingDelta> {

    // MySQL 8.0.20+ 에서 VALUES() 가 deprecated. row alias (AS new) 구문 사용.
    private static final String UPSERT_SQL =
        "INSERT INTO mv_product_rank_monthly_staging (year_month_key, product_id, score, updated_at) "
            + "VALUES (?, ?, ?, ?) AS new "
            + "ON DUPLICATE KEY UPDATE "
            + "score = mv_product_rank_monthly_staging.score + new.score, "
            + "updated_at = new.updated_at";

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['year_month']}")
    private String yearMonth;

    @Override
    public void write(Chunk<? extends StagingDelta> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        Map<Long, Double> aggregated = WeeklyStagingUpsertWriter.aggregateByProductId(chunk.getItems());
        List<Map.Entry<Long, Double>> rows = new ArrayList<>(aggregated.entrySet());
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(), (ps, entry) -> {
            ps.setString(1, yearMonth);
            ps.setLong(2, entry.getKey());
            ps.setDouble(3, entry.getValue());
            ps.setTimestamp(4, now);
        });
    }
}

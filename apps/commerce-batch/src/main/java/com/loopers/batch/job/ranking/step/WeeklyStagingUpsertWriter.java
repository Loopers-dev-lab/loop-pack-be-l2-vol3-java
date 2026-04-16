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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@StepScope
@RequiredArgsConstructor
public class WeeklyStagingUpsertWriter implements ItemWriter<StagingDelta> {

    // MySQL 8.0.20+ 에서 VALUES() 가 deprecated. row alias (AS new) 구문 사용.
    private static final String UPSERT_SQL =
        "INSERT INTO mv_product_rank_weekly_staging (year_week, product_id, score, updated_at) "
            + "VALUES (?, ?, ?, ?) AS new "
            + "ON DUPLICATE KEY UPDATE "
            + "score = mv_product_rank_weekly_staging.score + new.score, "
            + "updated_at = new.updated_at";

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['year_week']}")
    private String yearWeek;

    @Override
    public void write(Chunk<? extends StagingDelta> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        Map<Long, Double> aggregated = aggregateByProductId(chunk.getItems());
        List<Map.Entry<Long, Double>> rows = new ArrayList<>(aggregated.entrySet());
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(), (ps, entry) -> {
            ps.setString(1, yearWeek);
            ps.setLong(2, entry.getKey());
            ps.setDouble(3, entry.getValue());
            ps.setTimestamp(4, now);
        });
    }

    /**
     * chunk 내에 동일 product_id 가 여러 번 등장할 수 있다 (같은 상품의 7일치 row 등).
     * JDBC 드라이버의 rewriteBatchedStatements / ON DUPLICATE KEY UPDATE 내 동일 PK 동작이
     * 버전에 의존적이므로, 여기서 product_id 별로 합산해 batch 에 고유 PK 만 넣는다.
     */
    static Map<Long, Double> aggregateByProductId(List<? extends StagingDelta> items) {
        Map<Long, Double> aggregated = new LinkedHashMap<>();
        for (StagingDelta item : items) {
            aggregated.merge(item.productId(), item.delta(), Double::sum);
        }
        return aggregated;
    }
}

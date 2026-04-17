package com.loopers.batch.ranking.monthly;

import com.loopers.batch.ranking.RankingAggregateRow;
import com.loopers.domain.rank.BatchRankingWeightRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

@Configuration
public class MonthlyRankReaderConfig {

    @Bean
    @StepScope
    public JdbcCursorItemReader<RankingAggregateRow> monthlyRankReader(
            DataSource dataSource,
            BatchRankingWeightRepository weightRepository,
            @Value("#{jobParameters['snapshotDate']}") String snapshotDateStr) {

        BigDecimal viewWeight  = weightRepository.findWeightByEventType("VIEW");
        BigDecimal likeWeight  = weightRepository.findWeightByEventType("LIKE");
        BigDecimal orderWeight = weightRepository.findWeightByEventType("ORDER");

        String sql = buildSql(viewWeight, likeWeight, orderWeight);

        LocalDate snapshot = LocalDate.parse(snapshotDateStr);
        LocalDate windowStart = snapshot.minusDays(30);
        LocalDate windowEnd   = snapshot.minusDays(1);

        return new JdbcCursorItemReaderBuilder<RankingAggregateRow>()
            .name("monthlyRankReader")
            .dataSource(dataSource)
            .sql(sql)
            .preparedStatementSetter(ps -> {
                ps.setDate(1, Date.valueOf(windowStart));
                ps.setDate(2, Date.valueOf(windowEnd));
            })
            .rowMapper((rs, rowNum) -> new RankingAggregateRow(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getBigDecimal("order_revenue"),
                rs.getDouble("score")))
            .build();
    }

    private String buildSql(BigDecimal viewWeight, BigDecimal likeWeight, BigDecimal orderWeight) {
        return """
            SELECT product_id,
                   SUM(view_count)    AS view_count,
                   SUM(like_count)    AS like_count,
                   SUM(order_revenue) AS order_revenue,
                   LOG(1 + SUM(view_count))    * %s
                 + LOG(1 + SUM(like_count))    * %s
                 + LOG(1 + SUM(order_revenue)) * %s AS score
            FROM ranking_metrics
            WHERE metrics_date BETWEEN ? AND ?
            GROUP BY product_id
            ORDER BY score DESC
            LIMIT 100
            """.formatted(
                viewWeight.toPlainString(),
                likeWeight.toPlainString(),
                orderWeight.toPlainString());
    }
}

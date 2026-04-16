package com.loopers.batch.job.ranking.step;

import com.loopers.batch.domain.ranking.MonthRange;
import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.batch.job.ranking.dto.DailyLedgerRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Configuration
public class MonthlyLedgerReaderConfig {

    static final int PAGE_SIZE = 1000;
    static final String READER_BEAN_NAME = "monthlyLedgerReader";

    @Bean(READER_BEAN_NAME)
    @StepScope
    public JdbcPagingItemReader<DailyLedgerRow> monthlyLedgerReader(
        DataSource dataSource,
        @Value("#{jobParameters['year_month']}") String yearMonth
    ) {
        MonthRange range = MonthRange.of(yearMonth);

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("bucket_key", Order.ASCENDING);
        sortKeys.put("product_id", Order.ASCENDING);

        Map<String, Object> parameters = Map.of(
            "start", range.startKey(),
            "end", range.endKey()
        );

        return new JdbcPagingItemReaderBuilder<DailyLedgerRow>()
            .name(READER_BEAN_NAME)
            .dataSource(dataSource)
            .pageSize(PAGE_SIZE)
            .selectClause("SELECT product_id, bucket_key, base_points")
            .fromClause("FROM ranking_score_ledger")
            .whereClause("WHERE bucket_type = 'DAY' AND bucket_key BETWEEN :start AND :end")
            .sortKeys(sortKeys)
            .parameterValues(parameters)
            .rowMapper((rs, rowNum) -> new DailyLedgerRow(
                rs.getLong("product_id"),
                rs.getString("bucket_key"),
                rs.getDouble("base_points")
            ))
            .build();
    }
}

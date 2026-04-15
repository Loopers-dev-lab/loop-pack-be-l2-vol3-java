package com.loopers.batch.job.ranking.step;

import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ProductMetricsItemReader {

    private static final int PAGE_SIZE = 100;

    @StepScope
    @Bean("productMetricsItemReader")
    public JdbcPagingItemReader<ProductMetricsAggregatedDto> reader(
            DataSource dataSource,
            @Value("#{jobParameters['targetDate']}") String targetDate,
            @Value("#{jobParameters['period']}") String period) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate startDate = resolveStartDate(target, period);
        LocalDate endDate = resolveEndDate(target, period);

        log.info("ProductMetricsItemReader 기간: {} ~ {} (period={})", startDate, endDate, period);

        return new JdbcPagingItemReaderBuilder<ProductMetricsAggregatedDto>()
                .name("productMetricsItemReader")
                .dataSource(dataSource)
                .selectClause("product_id, SUM(view_count) AS total_view_count, SUM(like_count) AS total_like_count, SUM(total_quantity) AS total_quantity")
                .fromClause("product_metrics")
                .whereClause("metrics_date BETWEEN :startDate AND :endDate")
                .groupClause("product_id")
                .sortKeys(Map.of("product_id", Order.ASCENDING))
                .parameterValues(Map.of("startDate", Date.valueOf(startDate), "endDate", Date.valueOf(endDate)))
                .rowMapper((rs, rowNum) -> new ProductMetricsAggregatedDto(
                        rs.getLong("product_id"),
                        rs.getLong("total_view_count"),
                        rs.getLong("total_like_count"),
                        rs.getLong("total_quantity")
                ))
                .pageSize(PAGE_SIZE)
                .build();
    }

    private LocalDate resolveStartDate(LocalDate target, String period) {
        return switch (period) {
            case "weekly" -> target.with(DayOfWeek.MONDAY);
            case "monthly" -> target.withDayOfMonth(1);
            default -> throw new IllegalArgumentException("지원하지 않는 period: " + period);
        };
    }

    private LocalDate resolveEndDate(LocalDate target, String period) {
        return switch (period) {
            case "weekly" -> target.with(DayOfWeek.SUNDAY);
            case "monthly" -> target.withDayOfMonth(target.lengthOfMonth());
            default -> throw new IllegalArgumentException("지원하지 않는 period: " + period);
        };
    }
}

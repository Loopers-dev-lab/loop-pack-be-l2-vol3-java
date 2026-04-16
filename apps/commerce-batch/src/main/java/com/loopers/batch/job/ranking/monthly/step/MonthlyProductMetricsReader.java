package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.common.ProductAggregate;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 월간 집계 Reader Factory.
 * 집계 윈도우: {@code [monthStart, baseDate]} — baseDate의 달 1일부터 baseDate까지.
 * 진행 중 월의 경우 부분 집계, 마감 다음 날 실행 시 확정된 자연월 집계.
 */
public final class MonthlyProductMetricsReader {

    private static final int PAGE_SIZE = 1000;

    private MonthlyProductMetricsReader() {
    }

    public static JdbcPagingItemReader<ProductAggregate> create(DataSource dataSource, LocalDate baseDate) {
        LocalDate monthStart = baseDate.withDayOfMonth(1);

        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
                SELECT product_id,
                       SUM(view_count)   AS view_count,
                       SUM(like_count)   AS like_count,
                       SUM(order_count)  AS order_count,
                       SUM(order_amount) AS order_amount
                """);
        queryProvider.setFromClause("FROM product_metrics_daily");
        queryProvider.setWhereClause("WHERE metric_date BETWEEN :fromDate AND :toDate");
        queryProvider.setGroupClause("GROUP BY product_id");

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("product_id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("fromDate", monthStart);
        parameters.put("toDate", baseDate);

        return new JdbcPagingItemReaderBuilder<ProductAggregate>()
                .name("monthlyProductMetricsReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(parameters)
                .rowMapper(rowMapper())
                .pageSize(PAGE_SIZE)
                .build();
    }

    private static RowMapper<ProductAggregate> rowMapper() {
        return (rs, rowNum) -> new ProductAggregate(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("order_count"),
                rs.getLong("order_amount")
        );
    }
}

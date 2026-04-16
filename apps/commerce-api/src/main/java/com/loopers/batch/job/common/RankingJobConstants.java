package com.loopers.batch.job.common;

import java.time.format.DateTimeFormatter;

/**
 * 랭킹 집계 Job 공통 상수.
 */
public final class RankingJobConstants {

    private RankingJobConstants() {}

    public static final int CHUNK_SIZE = 100;
    public static final int TOP_N = 100;
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 메트릭 집계 SQL 템플릿.
     * {startDate}, {endDate}, {limit}을 치환하여 사용합니다.
     */
    public static final String AGGREGATION_SQL_TEMPLATE = """
        SELECT product_id,
               SUM(view_count) as total_view_count,
               SUM(like_count) as total_like_count,
               SUM(order_count) as total_order_count,
               SUM(score) as total_score
        FROM product_metrics_daily
        WHERE metric_date BETWEEN '{startDate}' AND '{endDate}'
        GROUP BY product_id
        ORDER BY total_score DESC
        LIMIT {limit}
        """;

    /**
     * 날짜 범위로 집계 SQL을 생성합니다.
     */
    public static String buildAggregationSql(String startDate, String endDate) {
        return AGGREGATION_SQL_TEMPLATE
            .replace("{startDate}", startDate)
            .replace("{endDate}", endDate)
            .replace("{limit}", String.valueOf(TOP_N));
    }
}
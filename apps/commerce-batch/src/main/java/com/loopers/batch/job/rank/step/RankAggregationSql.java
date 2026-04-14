package com.loopers.batch.job.rank.step;

public final class RankAggregationSql {

    public static final String AGGREGATE_BY_DATE_RANGE = """
            SELECT sd.product_db_id,
                   SUM(sd.score)          AS total_score,
                   SUM(sd.view_count)     AS total_view,
                   SUM(sd.like_count)     AS total_like,
                   SUM(sd.order_amount)   AS total_order
            FROM mv_product_score_daily sd
            WHERE sd.score_date BETWEEN ? AND ?
            GROUP BY sd.product_db_id
            ORDER BY total_score DESC, sd.product_db_id ASC
            LIMIT ?
            """;

    private RankAggregationSql() {
    }
}

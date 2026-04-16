package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;

public final class MonthlyTmpAggregateWriter {

    private static final String SQL = """
            INSERT INTO tmp_monthly_aggregate
                (product_id, view_count, like_count, order_count, order_amount, score)
            VALUES
                (:productId, :viewCount, :likeCount, :orderCount, :orderAmount, :score)
            ON DUPLICATE KEY UPDATE
                view_count   = VALUES(view_count),
                like_count   = VALUES(like_count),
                order_count  = VALUES(order_count),
                order_amount = VALUES(order_amount),
                score        = VALUES(score)
            """;

    private MonthlyTmpAggregateWriter() {
    }

    public static JdbcBatchItemWriter<ProductAggregateWithScore> create(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ProductAggregateWithScore>()
                .dataSource(dataSource)
                .sql(SQL)
                .itemSqlParameterSourceProvider(MonthlyTmpAggregateWriter::toParams)
                .build();
    }

    private static SqlParameterSource toParams(ProductAggregateWithScore item) {
        return new MapSqlParameterSource()
                .addValue("productId", item.productId())
                .addValue("viewCount", item.viewCount())
                .addValue("likeCount", item.likeCount())
                .addValue("orderCount", item.orderCount())
                .addValue("orderAmount", item.orderAmount())
                .addValue("score", item.score());
    }
}

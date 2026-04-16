package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;

/**
 * 주간 집계 결과를 {@code tmp_weekly_aggregate}에 upsert한다.
 *
 * <p>Reader의 {@code GROUP BY product_id}로 productId 중복은 이론상 없지만,
 * retry/재시도 시 안전장치로 ON DUPLICATE KEY UPDATE 사용.</p>
 *
 * <p>record 타입은 JavaBean getter 규약을 따르지 않으므로 beanMapped() 대신
 * 명시적 {@link MapSqlParameterSource}를 사용한다.</p>
 */
public final class WeeklyTmpAggregateWriter {

    private static final String SQL = """
            INSERT INTO tmp_weekly_aggregate
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

    private WeeklyTmpAggregateWriter() {
    }

    public static JdbcBatchItemWriter<ProductAggregateWithScore> create(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ProductAggregateWithScore>()
                .dataSource(dataSource)
                .sql(SQL)
                .itemSqlParameterSourceProvider(WeeklyTmpAggregateWriter::toParams)
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

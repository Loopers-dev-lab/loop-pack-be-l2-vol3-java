package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * product_order_metrics 의 sales_amount 를 cursor 로 스트리밍 + App 집계.
 * {@link ViewMetricStreamingReader} 와 동일한 패턴 (lookahead 직렬화 포함).
 */
@Component
@StepScope
public class OrderMetricStreamingReader implements ItemStreamReader<AggregatedMetric> {

    private static final int FETCH_SIZE = 2000;
    private static final String CTX_LOOKAHEAD_PRODUCT_ID  = "lookahead.productId";
    private static final String CTX_LOOKAHEAD_BUCKET_TIME = "lookahead.bucketTime";
    private static final String CTX_LOOKAHEAD_COUNT       = "lookahead.count";

    private final JdbcCursorItemReader<RawMetricRow> delegate;
    private final LocalDateTime last7dStart;
    private StreamingMetricAggregator aggregator;

    public OrderMetricStreamingReader(
            DataSource dataSource,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_7D_START + "']}") String last7dStart,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_30D_START + "']}") String last30dStart,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_30D_END + "']}") String last30dEnd
    ) {
        this.last7dStart = LocalDateTime.parse(last7dStart);
        LocalDateTime last30dStartTime = LocalDateTime.parse(last30dStart);
        LocalDateTime last30dEndTime = LocalDateTime.parse(last30dEnd);

        this.delegate = new JdbcCursorItemReaderBuilder<RawMetricRow>()
                .name("orderMetricCursorReader")
                .dataSource(dataSource)
                .fetchSize(FETCH_SIZE)
                .sql("""
                        SELECT product_id, bucket_time, sales_amount
                          FROM product_order_metrics
                         WHERE bucket_time >= ?
                           AND bucket_time <  ?
                         ORDER BY product_id, bucket_time
                        """)
                .preparedStatementSetter((ps) -> {
                    ps.setTimestamp(1, Timestamp.valueOf(last30dStartTime));
                    ps.setTimestamp(2, Timestamp.valueOf(last30dEndTime));
                })
                .rowMapper((rs, rowNum) -> new RawMetricRow(
                        rs.getLong("product_id"),
                        rs.getTimestamp("bucket_time").toLocalDateTime(),
                        rs.getLong("sales_amount")
                ))
                .build();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
        this.aggregator = new StreamingMetricAggregator(delegate::read, last7dStart);
        if (executionContext.containsKey(CTX_LOOKAHEAD_PRODUCT_ID)) {
            aggregator.setLookahead(new RawMetricRow(
                    executionContext.getLong(CTX_LOOKAHEAD_PRODUCT_ID),
                    LocalDateTime.parse(executionContext.getString(CTX_LOOKAHEAD_BUCKET_TIME)),
                    executionContext.getLong(CTX_LOOKAHEAD_COUNT)));
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
        RawMetricRow lookahead = aggregator.getLookahead();
        if (lookahead != null) {
            executionContext.putLong(CTX_LOOKAHEAD_PRODUCT_ID, lookahead.productId());
            executionContext.putString(CTX_LOOKAHEAD_BUCKET_TIME, lookahead.bucketTime().toString());
            executionContext.putLong(CTX_LOOKAHEAD_COUNT, lookahead.count());
        } else {
            executionContext.remove(CTX_LOOKAHEAD_PRODUCT_ID);
            executionContext.remove(CTX_LOOKAHEAD_BUCKET_TIME);
            executionContext.remove(CTX_LOOKAHEAD_COUNT);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }

    @Override
    public AggregatedMetric read() throws Exception {
        return aggregator.next();
    }
}

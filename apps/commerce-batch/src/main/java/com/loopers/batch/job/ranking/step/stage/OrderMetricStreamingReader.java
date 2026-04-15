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
 * 랭킹 스코어에 쓰이는 것은 salesAmount 이므로 여기서는 그 컬럼만 집계 대상으로 삼는다.
 */
@Component
@StepScope
public class OrderMetricStreamingReader implements ItemStreamReader<AggregatedMetric> {

    private static final int FETCH_SIZE = 2000;

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
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
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

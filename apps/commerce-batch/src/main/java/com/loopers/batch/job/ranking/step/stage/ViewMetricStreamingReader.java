package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import lombok.extern.slf4j.Slf4j;
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
 * product_view_metrics 를 (product_id, bucket_time) 순 cursor 로 스트리밍 읽고,
 * App 측 StreamingMetricAggregator 로 product 경계마다 AggregatedMetric 을 흘려보낸다.
 *
 * DB 는 단순 range scan 만 수행한다 (GROUP BY 없음). 집계는 App 책임.
 */
@Slf4j
@Component
@StepScope
public class ViewMetricStreamingReader implements ItemStreamReader<AggregatedMetric> {

    private static final int FETCH_SIZE = 2000;

    private final JdbcCursorItemReader<RawMetricRow> delegate;
    private final LocalDateTime last7dStart;
    private StreamingMetricAggregator aggregator;

    public ViewMetricStreamingReader(
            DataSource dataSource,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_7D_START + "']}") String last7dStart,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_30D_START + "']}") String last30dStart,
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_LAST_30D_END + "']}") String last30dEnd
    ) {
        this.last7dStart = LocalDateTime.parse(last7dStart);
        LocalDateTime last30dStartTime = LocalDateTime.parse(last30dStart);
        LocalDateTime last30dEndTime = LocalDateTime.parse(last30dEnd);

        this.delegate = new JdbcCursorItemReaderBuilder<RawMetricRow>()
                .name("viewMetricCursorReader")
                .dataSource(dataSource)
                .fetchSize(FETCH_SIZE)
                .sql("""
                        SELECT product_id, bucket_time, view_count
                          FROM product_view_metrics
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
                        rs.getLong("view_count")
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

package com.loopers.batch.job.ranking.step.stage;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * product_id 순서로 정렬된 raw row 들을 소비하며 product 경계마다 집계 결과를 내놓는다.
 *
 * <p>DB 가 GROUP BY 를 수행하지 않고 App 에서 스트리밍 집계하는 이유는 프롤로그
 * "배치의 본질 — 예측 가능성 > 평균" 원칙에서 도출된다.
 * 입력이 N배 튀어도 본 로직은 정확히 N배 시간만 선형으로 늘어난다.</p>
 *
 * <p>메모리는 한 상품의 누적 값 두 개(sum7d, sum30d) 만 유지한다 → O(1).</p>
 *
 * <p>전제: source 는 {@code null} 로 끝점을 알리며, row 는 product_id 로 이미 정렬되어 있다.</p>
 */
public final class StreamingMetricAggregator {

    public interface RowSource {
        RawMetricRow readOne() throws Exception;
    }

    private final RowSource source;
    private final LocalDateTime last7dStart;
    private RawMetricRow lookahead;
    private boolean exhausted;

    public StreamingMetricAggregator(RowSource source, LocalDateTime last7dStart) {
        this.source = Objects.requireNonNull(source);
        this.last7dStart = Objects.requireNonNull(last7dStart);
    }

    /**
     * 다음 product 의 집계 결과를 반환하거나, 더 이상 없으면 {@code null}.
     */
    public AggregatedMetric next() throws Exception {
        if (exhausted) {
            return null;
        }

        RawMetricRow current = (lookahead != null) ? lookahead : source.readOne();
        lookahead = null;
        if (current == null) {
            exhausted = true;
            return null;
        }

        Long productId = current.productId();
        long sum7d = 0L;
        long sum30d = 0L;

        while (current != null && productId.equals(current.productId())) {
            sum30d += current.count();
            if (!current.bucketTime().isBefore(last7dStart)) {
                sum7d += current.count();
            }
            current = source.readOne();
        }

        // 다음 product 의 첫 row 를 lookahead 에 보관 (다음 next() 호출에서 사용)
        lookahead = current;
        if (current == null) {
            exhausted = true;
        }
        return new AggregatedMetric(productId, sum7d, sum30d);
    }
}

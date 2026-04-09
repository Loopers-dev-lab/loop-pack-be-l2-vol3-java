package com.loopers.domain.metrics;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class MetricId implements Serializable {

    private Long productId;
    private LocalDateTime bucketTime;

    public MetricId() {
    }

    public MetricId(Long productId, LocalDateTime bucketTime) {
        this.productId = productId;
        this.bucketTime = bucketTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricId metricId = (MetricId) o;
        return Objects.equals(productId, metricId.productId)
                && Objects.equals(bucketTime, metricId.bucketTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, bucketTime);
    }
}

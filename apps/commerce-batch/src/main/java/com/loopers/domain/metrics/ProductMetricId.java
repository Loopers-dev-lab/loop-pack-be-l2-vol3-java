package com.loopers.domain.metrics;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class ProductMetricId implements Serializable {

    private Long productId;
    private LocalDateTime bucketTime;

    public ProductMetricId() {
    }

    public ProductMetricId(Long productId, LocalDateTime bucketTime) {
        this.productId = productId;
        this.bucketTime = bucketTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricId that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(bucketTime, that.bucketTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, bucketTime);
    }
}

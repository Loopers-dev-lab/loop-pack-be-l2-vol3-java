package com.loopers.domain.ranking;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * `ProductMetricsHourly` 의 복합키 클래스.
 *
 * JPA `@IdClass` 요구사항:
 * - 기본 생성자 (public)
 * - `Serializable`
 * - `equals()` / `hashCode()` 구현
 * - 엔티티의 `@Id` 필드와 동일한 이름/타입
 */
public class ProductMetricsHourlyId implements Serializable {

    private Long productId;
    private LocalDateTime bucketHour;

    public ProductMetricsHourlyId() {
    }

    public ProductMetricsHourlyId(Long productId, LocalDateTime bucketHour) {
        this.productId = productId;
        this.bucketHour = bucketHour;
    }

    public Long getProductId() {
        return productId;
    }

    public LocalDateTime getBucketHour() {
        return bucketHour;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricsHourlyId that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(bucketHour, that.bucketHour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, bucketHour);
    }
}

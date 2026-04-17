package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = PROTECTED)
@Embeddable
public class ProductMetricsDailyId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metrics_date", nullable = false)
    private LocalDate metricsDate;

    public ProductMetricsDailyId(Long productId, LocalDate metricsDate) {
        this.productId = productId;
        this.metricsDate = metricsDate;
    }
}

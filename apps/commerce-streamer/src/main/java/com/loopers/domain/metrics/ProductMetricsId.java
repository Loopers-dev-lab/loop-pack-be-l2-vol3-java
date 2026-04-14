package com.loopers.domain.metrics;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@EqualsAndHashCode
@NoArgsConstructor
public class ProductMetricsId implements Serializable {
    private Long productId;
    private LocalDate metricDate;
}

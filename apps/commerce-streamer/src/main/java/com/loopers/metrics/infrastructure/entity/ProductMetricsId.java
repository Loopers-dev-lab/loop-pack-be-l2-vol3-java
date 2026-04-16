package com.loopers.metrics.infrastructure.entity;


import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


/**
 * product_metrics 복합 PK — (metric_date, product_id)
 * - @IdClass 방식으로 JPA 복합키 선언
 */
public class ProductMetricsId implements Serializable {

	private LocalDate metricDate;
	private Long productId;


	public ProductMetricsId() {
	}

	public ProductMetricsId(LocalDate metricDate, Long productId) {
		this.metricDate = metricDate;
		this.productId = productId;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ProductMetricsId that)) return false;
		return Objects.equals(metricDate, that.metricDate) && Objects.equals(productId, that.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(metricDate, productId);
	}

}

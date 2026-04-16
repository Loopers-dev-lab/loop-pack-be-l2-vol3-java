package com.loopers.metrics.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 상품 메트릭 엔티티 (product_metrics — 일간 grain)
 * - PK: (metric_date, product_id) 복합키 — 주간/월간 집계를 위해 일간 row로 관리
 * - MetricsCollectorConsumer가 오늘 날짜 row에 delta 누적 + version 증가
 */

@Entity
@Table(
	name = "product_metrics",
	indexes = {
		// 주간/월간 Batch Reader가 metric_date BETWEEN ? AND ? + GROUP BY product_id로 집계
		// 카디널리티: product_id > metric_date
		@Index(name = "idx_product_metrics_product_date", columnList = "product_id, metric_date")
	}
)
@IdClass(ProductMetricsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsEntity {

	// 1. 복합 PK: (metric_date, product_id)
	@Id
	@Column(name = "metric_date", nullable = false)
	private LocalDate metricDate;

	@Id
	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private Long likeCount;

	@Column(nullable = false)
	private Long salesCount;

	@Column(nullable = false)
	private Long viewCount;

	@Column(nullable = false)
	private Long version;

	@Column(nullable = false)
	private LocalDateTime updatedAt;


	// 오늘 날짜 row 신규 생성 (upsert 시 존재하지 않을 때)
	public static ProductMetricsEntity createDefault(LocalDate metricDate, Long productId) {
		ProductMetricsEntity entity = new ProductMetricsEntity();
		entity.metricDate = metricDate;
		entity.productId = productId;
		entity.likeCount = 0L;
		entity.salesCount = 0L;
		entity.viewCount = 0L;
		entity.version = 0L;
		entity.updatedAt = LocalDateTime.now();
		return entity;
	}


	// delta 적용 + version 증가 (해당 날짜 row에 누적)
	public void applyDelta(long likeDelta, long salesDelta, long viewDelta) {
		this.likeCount = Math.max(0, this.likeCount + likeDelta);
		this.salesCount = Math.max(0, this.salesCount + salesDelta);
		this.viewCount = Math.max(0, this.viewCount + viewDelta);
		this.version++;
		this.updatedAt = LocalDateTime.now();
	}

}

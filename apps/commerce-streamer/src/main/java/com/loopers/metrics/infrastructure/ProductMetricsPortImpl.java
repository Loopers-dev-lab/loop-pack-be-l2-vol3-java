package com.loopers.metrics.infrastructure;


import com.loopers.metrics.application.port.out.ProductMetricsPort;
import com.loopers.metrics.infrastructure.entity.ProductMetricsEntity;
import com.loopers.metrics.infrastructure.jpa.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;


/**
 * 상품 메트릭 관리 포트 구현체
 * - 오늘 날짜 row에 delta를 누적하는 일간 grain upsert
 */

@Repository
@RequiredArgsConstructor
public class ProductMetricsPortImpl implements ProductMetricsPort {

	// jpa
	private final ProductMetricsJpaRepository productMetricsJpaRepository;


	/**
	 * 1. delta 적용 후 최신 snapshot 반환
	 * - 오늘 날짜 row가 없으면 신규 생성, 있으면 delta 누적
	 */
	@Override
	public MetricsSnapshot applyDeltaAndGet(Long productId, long likeDelta, long salesDelta, long viewDelta) {
		LocalDate today = LocalDate.now();

		// 오늘 날짜 row 조회 (없으면 신규 생성)
		ProductMetricsEntity metrics = productMetricsJpaRepository
			.findByMetricDateAndProductId(today, productId)
			.orElseGet(() -> ProductMetricsEntity.createDefault(today, productId));

		// delta 적용 + version 증가
		metrics.applyDelta(likeDelta, salesDelta, viewDelta);
		productMetricsJpaRepository.save(metrics);

		return new MetricsSnapshot(
			metrics.getProductId(),
			metrics.getLikeCount(),
			metrics.getSalesCount(),
			metrics.getViewCount(),
			metrics.getVersion(),
			metrics.getUpdatedAt().toString()
		);
	}

}

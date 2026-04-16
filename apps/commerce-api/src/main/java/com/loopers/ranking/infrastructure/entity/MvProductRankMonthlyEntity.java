package com.loopers.ranking.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 월간 랭킹 Materialized View 엔티티
 * - Batch가 staging → publish 방식으로 교체 적재
 * - API는 month_start_date + scorer_type 조건으로 조회
 */

@Entity
@Table(
	name = "mv_product_rank_monthly",
	indexes = {
		// API 조회: WHERE month_start_date = ? AND scorer_type = ? ORDER BY rank_position
		@Index(name = "idx_monthly_period_rank", columnList = "month_start_date, scorer_type, rank_position")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthlyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "month_start_date", nullable = false)
	private LocalDate monthStartDate;

	@Column(name = "month_key", nullable = false, length = 7)
	private String monthKey;

	@Column(name = "scorer_type", nullable = false, length = 30)
	private String scorerType;

	@Column(name = "rank_position", nullable = false)
	private int rankPosition;

	@Column(nullable = false, precision = 18, scale = 8)
	private BigDecimal score;

	@Column(name = "like_count", nullable = false)
	private Long likeCount;

	@Column(name = "sales_count", nullable = false)
	private Long salesCount;

	@Column(name = "view_count", nullable = false)
	private Long viewCount;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(name = "brand_id", nullable = false)
	private Long brandId;

	@Column(name = "brand_name", length = 100)
	private String brandName;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}

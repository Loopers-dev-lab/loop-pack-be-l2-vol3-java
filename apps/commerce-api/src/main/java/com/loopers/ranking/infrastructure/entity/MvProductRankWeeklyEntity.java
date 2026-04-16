package com.loopers.ranking.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 주간 랭킹 Materialized View 엔티티
 * - Batch가 staging → publish 방식으로 교체 적재
 * - API는 week_start_date + scorer_type 조건으로 조회
 */

@Entity
@Table(
	name = "mv_product_rank_weekly",
	indexes = {
		// API 조회: WHERE week_start_date = ? AND scorer_type = ? ORDER BY rank_position
		@Index(name = "idx_weekly_period_rank", columnList = "week_start_date, scorer_type, rank_position")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeeklyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(name = "week_end_date", nullable = false)
	private LocalDate weekEndDate;

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

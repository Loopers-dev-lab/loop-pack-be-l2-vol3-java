package com.loopers.domain.score;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "mv_product_score_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductScoreDailyModel {

    @EmbeddedId
    private MvProductScoreDailyId id;

    @Column(nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public MvProductScoreDailyModel(Long productDbId, LocalDate scoreDate, double score,
                                     long viewCount, long likeCount, BigDecimal orderAmount) {
        this.id = new MvProductScoreDailyId(productDbId, scoreDate);
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.orderAmount = orderAmount;
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}

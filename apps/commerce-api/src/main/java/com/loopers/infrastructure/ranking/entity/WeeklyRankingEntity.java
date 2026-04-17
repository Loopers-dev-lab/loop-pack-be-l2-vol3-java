package com.loopers.infrastructure.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "mv_product_rank_weekly")
@IdClass(WeeklyRankingEntity.WeeklyRankingId.class)
public class WeeklyRankingEntity {

    @Id
    @Column(nullable = false, length = 16)
    private String periodKey;

    @Id
    @Column(nullable = false)
    private int rankNo;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public record WeeklyRankingId(String periodKey, int rankNo) implements Serializable {
    }
}

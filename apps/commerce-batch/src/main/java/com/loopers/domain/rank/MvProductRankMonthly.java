package com.loopers.domain.rank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(name = "uk_snapshot_product", columnNames = {"snapshot_date", "product_id"}),
    indexes = @Index(name = "idx_snapshot_rank", columnList = "snapshot_date, rank_position")
)
public class MvProductRankMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_position", nullable = false)
    private int rank;

    @Column(nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_revenue", nullable = false, precision = 18, scale = 2)
    private BigDecimal orderRevenue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MvProductRankMonthly(LocalDate snapshotDate, Long productId, int rank, double score,
                                long viewCount, long likeCount, BigDecimal orderRevenue) {
        this.snapshotDate = snapshotDate;
        this.productId = productId;
        this.rank = rank;
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.orderRevenue = orderRevenue;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // JDBC RowMapper 전용 복원 팩토리
    public static MvProductRankMonthly reconstruct(Long id, LocalDate snapshotDate, Long productId,
                                                   int rank, double score, long viewCount, long likeCount,
                                                   BigDecimal orderRevenue, LocalDateTime createdAt) {
        MvProductRankMonthly row = new MvProductRankMonthly(snapshotDate, productId, rank, score, viewCount, likeCount, orderRevenue);
        row.id = id;
        row.createdAt = createdAt;
        return row;
    }
}

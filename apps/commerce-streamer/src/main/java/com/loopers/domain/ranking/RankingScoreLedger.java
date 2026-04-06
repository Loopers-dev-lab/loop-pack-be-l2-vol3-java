package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "ranking_score_ledger",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ranking_score_ledger_bucket_product",
        columnNames = {"bucket_type", "bucket_key", "product_id"}
    )
)
public class RankingScoreLedger extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket_type", nullable = false, length = 8)
    private BucketType bucketType;

    @Column(name = "bucket_key", nullable = false, length = 16)
    private String bucketKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "base_points", nullable = false)
    private double basePoints;

    @Column(name = "last_scored_at", nullable = false)
    private Instant lastScoredAt;

    @Column(name = "dirty", nullable = false)
    private boolean dirty;

    @Version
    @Column(name = "version")
    private Long version;

    public RankingScoreLedger(BucketType bucketType, String bucketKey, Long productId) {
        Assert.notNull(bucketType, "bucketType must not be null");
        Assert.hasText(bucketKey, "bucketKey must not be blank");
        Assert.notNull(productId, "productId must not be null");
        this.bucketType = bucketType;
        this.bucketKey = bucketKey;
        this.productId = productId;
        this.basePoints = 0.0;
        this.lastScoredAt = Instant.now();
        this.dirty = false;
    }

    public void addScore(double delta) {
        this.basePoints += delta;
        this.lastScoredAt = Instant.now();
        this.dirty = true;
    }

    public void markSynced() {
        this.dirty = false;
    }

    public enum BucketType {
        DAY, HOUR
    }
}

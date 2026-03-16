package com.loopers.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_like_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLikeStats {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "synced_at", nullable = false)
    private ZonedDateTime syncedAt;

    public ProductLikeStats(Long productId, int likeCount) {
        this.productId = productId;
        this.likeCount = likeCount;
        this.syncedAt = ZonedDateTime.now();
    }

    public void updateCount(int likeCount) {
        this.likeCount = likeCount;
        this.syncedAt = ZonedDateTime.now();
    }
}

package com.loopers.infrastructure.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "product_like_stats")
public class ProductLikeStatsEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private long likeCount;

    protected ProductLikeStatsEntity() {}

    public static ProductLikeStatsEntity create(Long productId) {
        ProductLikeStatsEntity entity = new ProductLikeStatsEntity();
        entity.productId = productId;
        entity.likeCount = 0;
        return entity;
    }
}

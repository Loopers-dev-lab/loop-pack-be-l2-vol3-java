package com.loopers.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 상품별 집계 수치(비정규화). 좋아요 수 정렬·PDP 노출용.
 * 로드맵: product_stats 수직 분리, like_count 아토믹 업데이트.
 * likes_desc 정렬용 인덱스: (like_count, product_id).
 */
@Entity
@Table(name = "product_stats", indexes = {
        @Index(name = "idx_product_stats_like_count", columnList = "like_count, product_id")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ProductStatsModel {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    private ProductStatsModel(Long productId, long likeCount) {
        this.productId = productId;
        this.likeCount = likeCount;
    }

    public static ProductStatsModel zero(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId는 null일 수 없습니다.");
        }
        return new ProductStatsModel(productId, 0L);
    }

    public long getLikeCount() {
        return likeCount;
    }
}

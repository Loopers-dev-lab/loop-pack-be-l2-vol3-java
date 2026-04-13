package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.metrics.ProductMetrics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_metrics_product_id_date", columnNames = {"product_id", "metrics_date"})
    }
)
@NoArgsConstructor
public class ProductMetricsEntity extends BaseEntity {

    @Comment("상품 id (ref)")
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Comment("집계 기준 날짜")
    @Column(name = "metrics_date", nullable = false, updatable = false)
    private LocalDate metricsDate;

    @Comment("좋아요 수 집계")
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Comment("조회 수 집계")
    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Comment("판매량 집계")
    @Column(name = "sales_count", nullable = false)
    private int salesCount;

    @Comment("주문 수량 합산")
    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    public ProductMetricsEntity(ProductMetrics metrics) {
        this.productId = metrics.getProductId();
        this.metricsDate = metrics.getMetricsDate();
        this.likeCount = metrics.getLikeCount();
        this.viewCount = metrics.getViewCount();
        this.salesCount = metrics.getSalesCount();
        this.totalQuantity = metrics.getTotalQuantity();
    }

    public static ProductMetrics toDomain(ProductMetricsEntity entity) {
        return ProductMetrics.restore(
            entity.getId(),
            entity.productId,
            entity.metricsDate,
            entity.likeCount,
            entity.viewCount,
            entity.salesCount,
            entity.totalQuantity
        );
    }

    public Long getProductId() {
        return productId;
    }

    public LocalDate getMetricsDate() {
        return metricsDate;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }
}

package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "product_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_metrics_product_hour",
                columnNames = {"product_id", "metric_hour"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metric_hour", nullable = false)
    private LocalDateTime metricHour;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "order_count", nullable = false)
    private Long orderCount = 0L;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    private ProductMetrics(Long productId, LocalDateTime metricHour) {
        this.productId = productId;
        this.metricHour = metricHour;
    }

    public static ProductMetrics of(Long productId, LocalDateTime metricHour) {
        return new ProductMetrics(productId, metricHour);
    }

    public Long likeCount() {
        return likeCount;
    }

    public Long orderCount() {
        return orderCount;
    }

    public Long salesAmount() {
        return salesAmount;
    }

    public void applyLike(int delta) {
        this.likeCount += delta;
    }

    public void applyOrder(long quantity, long salesAmount) {
        this.orderCount += quantity;
        this.salesAmount += salesAmount;
    }

    public void applyView() {
        this.viewCount += 1;
    }

    /**
     * 엔티티의 유효성을 검증한다.
     * 이 메소드는 PrePersist 및 PreUpdate 시점에 호출된다.
     */
    protected void guard() {}

    @PrePersist
    private void prePersist() {
        guard();

        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        guard();

        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * delete 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)
     */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * restore 연산은 멱등하게 동작할 수 있도록 한다. (삭제되지 않은 엔티티를 복원해도 동일한 결과가 나오도록)
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }
}

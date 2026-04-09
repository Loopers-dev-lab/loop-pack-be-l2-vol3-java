package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_order_metrics", indexes = {
        @Index(name = "idx_pom_bucket_time", columnList = "bucket_time")
})
@IdClass(MetricId.class)
@Getter
public class ProductOrderMetric {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "bucket_time")
    private LocalDateTime bucketTime;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    protected ProductOrderMetric() {
    }
}

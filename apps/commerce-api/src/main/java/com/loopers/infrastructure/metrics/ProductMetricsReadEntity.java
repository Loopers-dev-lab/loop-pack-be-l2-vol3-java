package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsReadEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "view_count")
    private long viewCount;

    @Column(name = "like_count")
    private long likeCount;

    @Column(name = "sales_count")
    private long salesCount;

    @Column(name = "version")
    private long version;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}

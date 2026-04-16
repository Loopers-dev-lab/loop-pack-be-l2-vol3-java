package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "product_metrics")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_quantity", nullable = false)
    private Long salesQuantity;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;
}

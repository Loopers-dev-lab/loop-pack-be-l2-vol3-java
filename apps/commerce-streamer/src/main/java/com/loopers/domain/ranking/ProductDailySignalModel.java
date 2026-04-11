package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "product_daily_signals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_daily_signals_product_date", columnNames = {"product_db_id", "signal_date"})
        }
)
@Getter
public class ProductDailySignalModel extends BaseEntity {

    @Column(name = "product_db_id", nullable = false)
    private Long productDbId;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderAmount;

    protected ProductDailySignalModel() {}
}

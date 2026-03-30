package com.loopers.domain.collector;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorUserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private CollectorCouponModel coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CollectorUserCouponStatus status;

    @Column(name = "order_id")
    private Long orderId;

    public CollectorUserCouponModel(Long userId, CollectorCouponModel coupon) {
        this.userId = userId;
        this.coupon = coupon;
        this.status = CollectorUserCouponStatus.AVAILABLE;
    }
}

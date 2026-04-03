package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * commerce-streamer에서 쿠폰 발급 Consumer가 참조하는 읽기/쓰기용 엔티티.
 * commerce-api의 CouponModel과 같은 테이블(coupon)을 매핑한다.
 */
@Getter
@Entity
@Table(name = "coupon")
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    protected CouponEntity() {
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public boolean isQuantityAvailable() {
        if (maxQuantity == null) return true;
        return issuedCount < maxQuantity;
    }

    public void incrementIssuedCount() {
        this.issuedCount++;
    }
}

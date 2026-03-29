package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Coupon {

    @Id
    private Long id;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    public void issue() {
        if (issuedCount >= totalQuantity) {
            throw new IllegalStateException("쿠폰 수량이 소진되었습니다: couponId=" + id);
        }
        this.issuedCount++;
    }

    public boolean isExpired() {
        return !expiredAt.isAfter(ZonedDateTime.now());
    }
}

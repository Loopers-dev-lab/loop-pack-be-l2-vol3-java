package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "expired_at")
    private ZonedDateTime expiredAt;

    @Column(name = "name")
    private String name;

    @Column(name = "coupon_type")
    private String couponType;

    @Column(name = "value")
    private int value;

    public Long getId() {
        return id;
    }

    public Integer totalQuantity() {
        return totalQuantity;
    }

    public boolean isLimited() {
        return totalQuantity != null;
    }

    public ZonedDateTime expiredAt() {
        return expiredAt;
    }

    public String name() {
        return name;
    }

    public String couponType() {
        return couponType;
    }

    public int value() {
        return value;
    }
}

package com.loopers.infrastructure.coupon.entity;

import com.loopers.domain.coupon.model.FirstComeCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "first_come_coupon")
public class FirstComeCouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponTemplateId;

    @Column(nullable = false)
    private int maxQuantity;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    public FirstComeCoupon toModel() {
        return FirstComeCoupon.reconstruct(id, couponTemplateId, maxQuantity, startAt, endAt);
    }
}

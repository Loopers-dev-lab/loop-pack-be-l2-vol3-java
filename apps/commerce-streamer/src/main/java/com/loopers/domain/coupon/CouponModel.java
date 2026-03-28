package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int issuedQuantity;

    public int getRemainingQuantity() {
        if (totalQuantity == 0) return Integer.MAX_VALUE;
        return totalQuantity - issuedQuantity;
    }

    public void increaseIssuedQuantity() {
        if (totalQuantity > 0 && getRemainingQuantity() <= 0) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }
        this.issuedQuantity++;
    }
}

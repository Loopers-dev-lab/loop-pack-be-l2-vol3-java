package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupons")
@Getter
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    protected Coupon() {}

    public Coupon(String name, Integer totalQuantity) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.");
        }
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 수량은 0보다 커야 합니다.");
        }
        this.name = name;
        this.totalQuantity = totalQuantity;
    }
}

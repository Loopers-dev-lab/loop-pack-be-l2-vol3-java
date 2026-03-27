package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupon_templates")
@Getter
public class CouponTemplateModel extends BaseEntity {

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    protected CouponTemplateModel() {}

    public boolean isQuantityLimited() {
        return totalQuantity != null;
    }
}

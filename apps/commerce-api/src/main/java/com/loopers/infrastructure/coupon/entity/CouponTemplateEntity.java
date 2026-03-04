package com.loopers.infrastructure.coupon.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.support.CouponEnums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coupon_template")
@SQLRestriction("deleted_at IS NULL")
public class CouponTemplateEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponEnums.Type type;

    @Column(nullable = false)
    private int discountValue;

    @Column(nullable = false)
    private int minOrderAmount;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    protected CouponTemplateEntity() {}

    private CouponTemplateEntity(String name, CouponEnums.Type type, int discountValue,
                                  int minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplateEntity toEntity(CouponTemplate model) {
        return new CouponTemplateEntity(
                model.getName().value(),
                model.getType(),
                model.getDiscountValue().value(),
                model.getMinOrderAmount().value(),
                model.getExpiredAt()
        );
    }

    public CouponTemplate toModel() {
        return CouponTemplate.reconstruct(
                getId(),
                this.name,
                this.type.name(),
                this.discountValue,
                this.minOrderAmount,
                this.expiredAt
        );
    }

    public void update(String name, CouponEnums.Type type, int discountValue,
                       int minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }
}

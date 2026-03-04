package com.loopers.infrastructure.coupon.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.model.UserCoupon;
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
@Table(name = "user_coupon")
@SQLRestriction("deleted_at IS NULL")
public class UserCouponEntity extends BaseEntity {

    @Column(nullable = false)
    private Long couponTemplateId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponEnums.Status status;

    private LocalDateTime usedAt;

    protected UserCouponEntity() {}

    private UserCouponEntity(Long couponTemplateId, Long memberId, CouponEnums.Status status,
                              LocalDateTime usedAt) {
        this.couponTemplateId = couponTemplateId;
        this.memberId = memberId;
        this.status = status;
        this.usedAt = usedAt;
    }

    public static UserCouponEntity toEntity(UserCoupon model) {
        return new UserCouponEntity(
                model.getCouponTemplateId(),
                model.getMemberId(),
                model.getStatus(),
                model.getUsedAt()
        );
    }

    public UserCoupon toModel() {
        return UserCoupon.reconstruct(
                getId(),
                this.couponTemplateId,
                this.memberId,
                this.status.name(),
                this.usedAt
        );
    }

    public void updateStatus(CouponEnums.Status status, LocalDateTime usedAt) {
        this.status = status;
        this.usedAt = usedAt;
    }
}

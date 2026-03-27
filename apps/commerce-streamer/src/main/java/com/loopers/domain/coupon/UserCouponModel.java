package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_coupon_member_template",
                        columnNames = {"ref_member_id", "ref_coupon_template_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Column(name = "ref_member_id", nullable = false)
    private Long refMemberId;

    @Column(name = "ref_coupon_template_id", nullable = false)
    private Long refCouponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private UserCouponStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    private UserCouponModel(Long refMemberId, Long refCouponTemplateId) {
        this.refMemberId = refMemberId;
        this.refCouponTemplateId = refCouponTemplateId;
        this.status = UserCouponStatus.AVAILABLE;
        this.version = 0;
    }

    public static UserCouponModel create(Long memberId, Long templateId) {
        return new UserCouponModel(memberId, templateId);
    }
}

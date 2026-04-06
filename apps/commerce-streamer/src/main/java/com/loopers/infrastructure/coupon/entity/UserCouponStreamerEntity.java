package com.loopers.infrastructure.coupon.entity;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_coupon", uniqueConstraints =
        @UniqueConstraint(columnNames = {"memberId", "couponTemplateId"}))
public class UserCouponStreamerEntity extends BaseEntity {

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    @Column(nullable = false)
    private Long couponTemplateId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String status;

    @Version
    private Long version;

    public static UserCouponStreamerEntity create(Long couponTemplateId, Long memberId) {
        UserCouponStreamerEntity entity = new UserCouponStreamerEntity();
        entity.couponTemplateId = couponTemplateId;
        entity.memberId = memberId;
        entity.status = STATUS_AVAILABLE;
        return entity;
    }
}

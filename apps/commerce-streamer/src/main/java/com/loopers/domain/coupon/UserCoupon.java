package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "coupon_type", nullable = false)
    private String couponType;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    private UserCoupon(Long userId, Coupon coupon) {
        this.userId = userId;
        this.couponTemplateId = coupon.getId();
        this.name = coupon.name();
        this.couponType = coupon.couponType();
        this.value = coupon.value();
        this.expiredAt = coupon.expiredAt();
        this.status = "AVAILABLE";
        this.version = 0L;
    }

    public static UserCoupon of(Long userId, Coupon coupon) {
        return new UserCoupon(userId, coupon);
    }

    /**
     * 엔티티의 유효성을 검증한다.
     * 이 메소드는 PrePersist 및 PreUpdate 시점에 호출된다.
     */
    protected void guard() {}

    @PrePersist
    private void prePersist() {
        guard();

        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        guard();

        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * delete 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)
     */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * restore 연산은 멱등하게 동작할 수 있도록 한다. (삭제되지 않은 엔티티를 복원해도 동일한 결과가 나오도록)
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }
}

package com.loopers.domain.coupon;

import jakarta.persistence.*;
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

    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    @Column(name = "expired_at")
    private ZonedDateTime expiredAt;

    @Column(name = "name")
    private String name;

    @Column(name = "coupon_type")
    private String couponType;

    @Column(name = "value")
    private int value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public Integer totalQuantity() {
        return totalQuantity;
    }

    public boolean isLimited() {
        return totalQuantity != null;
    }

    public boolean hasRemainingQuantity() {
        return remainingQuantity != null && remainingQuantity > 0;
    }

    public void decreaseRemainingQuantity() {
        this.remainingQuantity -= 1;
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

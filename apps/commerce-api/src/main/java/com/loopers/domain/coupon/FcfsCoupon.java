package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "fcfs_coupons")
public class FcfsCoupon extends BaseEntity {

    @Column(name = "coupon_id", nullable = false, unique = true)
    private Long couponId;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "opened_at", nullable = false)
    private ZonedDateTime openedAt;

    @Column(name = "closed_at", nullable = false)
    private ZonedDateTime closedAt;

    protected FcfsCoupon() {}

    public FcfsCoupon(Long couponId, int maxQuantity, ZonedDateTime openedAt, ZonedDateTime closedAt) {
        Assert.notNull(couponId, "couponId는 필수입니다.");
        Assert.state(maxQuantity > 0, "최대 수량은 0보다 커야 합니다.");
        Assert.notNull(openedAt, "오픈 시간은 필수입니다.");
        Assert.notNull(closedAt, "마감 시간은 필수입니다.");
        Assert.state(closedAt.isAfter(openedAt), "마감 시간은 오픈 시간 이후여야 합니다.");
        this.couponId = couponId;
        this.maxQuantity = maxQuantity;
        this.issuedCount = 0;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }

    public void validateIssuable() {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(openedAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아직 쿠폰 발급 기간이 아닙니다.");
        }
        if (now.isAfter(closedAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 종료되었습니다.");
        }
        if (isSoldOut()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
        }
    }

    public boolean isSoldOut() {
        return issuedCount >= maxQuantity;
    }

    public void incrementIssuedCount() {
        this.issuedCount++;
    }
}

package com.loopers.domain.collector;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorCouponModel extends BaseEntity {

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "issue_limit")
    private Long issueLimit;

    @Column(name = "issued_count", nullable = false)
    private Long issuedCount;

    public CollectorCouponModel(ZonedDateTime expiredAt, Long issueLimit) {
        this.expiredAt = expiredAt;
        this.issueLimit = issueLimit;
        this.issuedCount = 0L;
    }

    public boolean canIssue() {
        if (getDeletedAt() != null) {
            return false;
        }
        if (!expiredAt.isAfter(ZonedDateTime.now())) {
            return false;
        }
        long currentIssuedCount = issuedCount == null ? 0L : issuedCount;
        return issueLimit == null || currentIssuedCount < issueLimit;
    }

    public void reserveIssue() {
        if (issuedCount == null) {
            issuedCount = 0L;
        }
        issuedCount += 1;
    }
}

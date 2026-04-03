package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Consumer용 CouponTemplate 엔티티.
 * commerce-api의 CouponTemplate과 같은 테이블을 바라본다.
 * Consumer에서 필요한 필드만 포함.
 */
@Entity
@Table(name = "coupon_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "max_issue_count")
    private Integer maxIssueCount;

    @Column(name = "current_issued_count", nullable = false)
    private int currentIssuedCount;

    public boolean canIssue() {
        return maxIssueCount == null || currentIssuedCount < maxIssueCount;
    }

    public void incrementIssuedCount() {
        this.currentIssuedCount++;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }
}

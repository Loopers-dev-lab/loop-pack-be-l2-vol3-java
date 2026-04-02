package com.loopers.domain.fcfs;

import java.time.Instant;
import java.util.Objects;

/**
 * 선착순 쿠폰 도메인 모델.
 */
public class FcfsCoupon {

    private final Long id;
    private final String name;
    private final int totalQuantity;
    private int issuedCount;
    private final Instant createdAt;

    private FcfsCoupon(Long id, String name, int totalQuantity, int issuedCount, Instant createdAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.totalQuantity = totalQuantity;
        this.issuedCount = issuedCount;
        this.createdAt = createdAt;
    }

    public static FcfsCoupon create(String name, int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("totalQuantity must be positive");
        }
        return new FcfsCoupon(null, name, totalQuantity, 0, Instant.now());
    }

    public static FcfsCoupon reconstitute(Long id, String name, int totalQuantity, int issuedCount, Instant createdAt) {
        return new FcfsCoupon(id, name, totalQuantity, issuedCount, createdAt);
    }

    public boolean canIssue() {
        return issuedCount < totalQuantity;
    }

    public void incrementIssuedCount() {
        if (!canIssue()) {
            throw new IllegalStateException("Cannot issue coupon: quantity exhausted");
        }
        this.issuedCount++;
    }

    public int remainingQuantity() {
        return totalQuantity - issuedCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getIssuedCount() {
        return issuedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

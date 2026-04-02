package com.loopers.infrastructure.persistence.jpa.fcfs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 선착순 쿠폰 JPA 엔티티 (commerce-collector용).
 */
@Entity
@Table(name = "fcfs_coupon")
public class FcfsCouponJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FcfsCouponJpaEntity() {}

    @PrePersist
    private void prePersist() {
        this.createdAt = Instant.now();
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

    public void setIssuedCount(int issuedCount) {
        this.issuedCount = issuedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean canIssue() {
        return issuedCount < totalQuantity;
    }
}

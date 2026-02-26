package com.loopers.infrastructure.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "order_snapshots")
public class OrderSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotData;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrderSnapshotJpaEntity() {}

    public OrderSnapshotJpaEntity(Long id, String snapshotData, LocalDateTime createdAt) {
        this.id = id;
        this.snapshotData = snapshotData;
        this.createdAt = createdAt;
    }
}

package com.loopers.infrastructure.like;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "productId"})
})
public class LikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LikeJpaEntity() {}

    public LikeJpaEntity(Long id, String userId, Long productId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.createdAt = createdAt;
    }
}

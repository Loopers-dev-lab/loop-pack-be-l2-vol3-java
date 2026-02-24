package com.loopers.domain.like;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "likes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_likes_member_product", columnNames = {"member_id", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public Like(Long memberId, Long productId) {
        this.memberId = memberId;
        this.productId = productId;
        this.createdAt = ZonedDateTime.now();
    }
}

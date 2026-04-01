package com.loopers.infrastructure.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * products 테이블의 like_count 업데이트 전용 경량 Entity (Streamer용)
 *
 * ProductEntity(commerce-api)의 전체 필드를 매핑하지 않고,
 * like_count 원자적 UPDATE에 필요한 최소 필드만 매핑한다.
 */
@Entity
@Table(name = "products")
public class ProductLikeCountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    protected ProductLikeCountEntity() {}

    public Long getId() { return id; }
    public Integer getLikeCount() { return likeCount; }
}

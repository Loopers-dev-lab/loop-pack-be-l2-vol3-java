package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 배치 모듈용 Product 읽기 전용 엔티티.
 * 배치 집계 SQL에서 deleted_at 필터링을 위해 테이블 스키마가 필요하다.
 */
@Entity
@Table(name = "products")
@Getter
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Product() {
    }
}

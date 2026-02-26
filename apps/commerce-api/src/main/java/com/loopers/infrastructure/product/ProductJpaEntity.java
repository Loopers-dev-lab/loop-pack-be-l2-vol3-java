package com.loopers.infrastructure.product;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private int likeCount;

    private String description;

    private Integer salePrice;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    protected ProductJpaEntity() {}

    public ProductJpaEntity(Long id, Long brandId, String name, int price, Integer salePrice,
                            int stockQuantity, int likeCount, String description,
                            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.salePrice = salePrice;
        this.stockQuantity = stockQuantity;
        this.likeCount = likeCount;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }
}

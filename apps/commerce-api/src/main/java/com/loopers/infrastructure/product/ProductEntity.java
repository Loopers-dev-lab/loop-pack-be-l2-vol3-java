package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 *  Product DB 엔티티
 */
@Entity
@Table(name = "product")
@NoArgsConstructor
public class ProductEntity extends BaseEntity {

    @Comment("상품 이름")
    @Column(name = "name", nullable = false)
    private String name;

    @Comment("브랜드 id (ref)")
    @Column(name = "ref_brand_id", nullable = false, updatable = false)
    private Long refBrandId;

    @Comment("현재 판매가")
    @Column(name = "price", nullable = false)
    private Integer price;

    @Comment("현재 재고")
    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Comment("좋아요 수")
    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    public ProductEntity(Product product) {
        this.name = product.getName();
        this.refBrandId = product.getRefBrandId();
        this.price = product.getPrice().value();
        this.likeCount = product.getLikeCount();
    }

    public static ProductEntity create(Product product) {
        return new ProductEntity(product);
    }

    public static Product toDomain(ProductEntity productEntity) {
        return Product.create(
            productEntity.getId(),
            productEntity.getName(),
            productEntity.getRefBrandId(),
            productEntity.getPrice(),
            productEntity.getStock(),
            productEntity.getLikeCount()
        );
    }

    public void update(Product product) {
        this.name = product.getName();
        this.refBrandId = product.getRefBrandId();
        this.price = product.getPrice().value();
        this.stock = product.getStock();
    }

    public String getName() {
        return name;
    }

    public Long getRefBrandId() {
        return refBrandId;
    }

    public Integer getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getLikeCount() {
        return likeCount;
    }
}

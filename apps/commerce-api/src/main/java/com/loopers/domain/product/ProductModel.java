package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_deleted_created_id", columnList = "deleted_at, created_at, id"),
        @Index(name = "idx_products_deleted_price_id", columnList = "deleted_at, price, id"),
        @Index(name = "idx_products_brand_deleted_like_id", columnList = "brand_id, deleted_at, like_count, id"),
        @Index(name = "idx_products_deleted_like_id", columnList = "deleted_at, like_count, id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandModel brand;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "description")
    private String description;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    public ProductModel(BrandModel brand, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        validate(brand, name, price, stockQuantity, status);
        this.brand = brand;
        this.name = name;
        this.price = price;
        this.description = description;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.likeCount = 0L;
    }

    public void update(BrandModel brand, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        validate(brand, name, price, stockQuantity, status);
        this.brand = brand;
        this.name = name;
        this.price = price;
        this.description = description;
        this.stockQuantity = stockQuantity;
        this.status = status;
    }

    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stockQuantity < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    public void increaseLikeCount() {
        this.likeCount += 1;
    }

    public void decreaseLikeCount() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.CONFLICT, "좋아요 수가 이미 0입니다.");
        }
        this.likeCount -= 1;
    }

    private void validate(BrandModel brand, String name, Long price, int stockQuantity, ProductStatus status) {
        if (brand == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 필수입니다.");
        }
        if (!StringUtils.hasText(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
        if (stockQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 상태는 필수입니다.");
        }
    }
}

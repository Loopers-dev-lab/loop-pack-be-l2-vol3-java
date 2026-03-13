package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = {
        // 핵심 인덱스: 브랜드 필터 + 정렬
        @Index(name = "idx_products_brand_created", columnList = "deleted_at, brand_id, created_at DESC"),
        @Index(name = "idx_products_brand_price", columnList = "deleted_at, brand_id, price"),
        @Index(name = "idx_products_brand_likes", columnList = "deleted_at, brand_id, like_count DESC"),
        // 방어 인덱스: 브랜드 필터 없는 전체 조회 (캐시 미스 대비)
        @Index(name = "idx_products_created_only", columnList = "deleted_at, created_at DESC"),
        @Index(name = "idx_products_likes_only", columnList = "deleted_at, like_count DESC"),
        @Index(name = "idx_products_price_only", columnList = "deleted_at, price")
})
@Getter
public class Product extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;
    private static final BigDecimal PRICE_MAX = new BigDecimal("999999999");
    private static final int STOCK_QUANTITY_MAX = 9_999_999;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Version
    private Long version;

    protected Product() {
    }

    private Product(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = description;
        this.likeCount = 0;
    }

    public static Product create(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStockQuantity(stockQuantity);
        validateDescription(description);
        return new Product(brandId, name, price, stockQuantity, description);
    }

    public void updateInfo(String name, BigDecimal price, Integer stockQuantity, String description) {
        validateNotDeleted();
        if (name != null) {
            validateName(name);
            this.name = name;
        }
        if (price != null) {
            validatePrice(price);
            this.price = price;
        }
        if (stockQuantity != null) {
            validateStockQuantity(stockQuantity);
            this.stockQuantity = stockQuantity;
        }
        if (description != null) {
            validateDescription(description);
            this.description = description;
        }
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public void validateStockSufficient(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다");
        }
    }

    private void validateNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다");
        }
    }

    private static void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 200자 이하여야 합니다");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다");
        }
        if (price.compareTo(PRICE_MAX) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 999,999,999 이하여야 합니다");
        }
    }

    private static void validateStockQuantity(Integer stockQuantity) {
        if (stockQuantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 필수입니다");
        }
        if (stockQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다");
        }
        if (stockQuantity > STOCK_QUANTITY_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 9,999,999 이하여야 합니다");
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 1,000자 이하여야 합니다");
        }
    }
}

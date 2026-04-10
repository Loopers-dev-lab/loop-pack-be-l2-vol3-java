package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_price", columnList = "price"),
        @Index(name = "idx_products_created", columnList = "created_at DESC"),
        @Index(name = "idx_products_brand_price", columnList = "brand_id, price"),
        @Index(name = "idx_products_brand_created", columnList = "brand_id, created_at DESC")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Version
    private Long version;

    // === 생성 === //

    private ProductModel(Long brandId, String name, int price, int stock) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static ProductModel create(Long brandId, String name, int price, int stock) {
        validateBrandId(brandId);
        validateName(name);
        validatePriceRange(price);
        validateStockRange(stock);
        return new ProductModel(brandId, name, price, stock);
    }

    // === 도메인 로직 === //

    public void update(String name, int price, int stock) {
        validateName(name);
        validatePriceRange(price);
        validateStockRange(stock);
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void validateExpectedPrice(int expectedPrice) {
        if (expectedPrice != this.price) {
            throw new CoreException(ProductErrorCode.PRICE_MISMATCH);
        }
    }

    public void decreaseStock(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
    }

    public boolean isSoldOut() {
        return this.stock == 0;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    // === 컬렉션 유틸 === //

    public static List<Long> extractDistinctBrandIds(List<ProductModel> products) {
        return products.stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();
    }

    public static List<Long> extractIds(List<ProductModel> products) {
        return products.stream()
                .map(ProductModel::getId)
                .toList();
    }

    // === 검증 === //

    private static void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수값입니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수값입니다.");
        }
        if (name.length() > 99) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 99자 이하여야 합니다.");
        }
    }

    private static void validatePriceRange(int price) {
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    private static void validateStockRange(int stock) {
        if (stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}

package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "product",
    indexes = {
        // 브랜드 필터링용 단일 인덱스
        @Index(name = "idx_product_brand_id", columnList = "brand_id"),
        // 좋아요 순 정렬용 인덱스 (비정규화된 likesCount 활용)
        @Index(name = "idx_product_likes_count", columnList = "likes_count DESC")
    }
)
public class Product extends BaseEntity {

    private Long brandId;
    private String name;
    private Long price;
    private int stockQuantity;
    // 비정규화: Like 테이블 집계 연산(COUNT + GROUP BY) 제거 목적
    // 좋아요 등록/취소 시 LikeService에서 동기화
    private long likesCount;

    protected Product() {}

    public Product(Long brandId, String name, Long price, int stockQuantity) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStockQuantity(stockQuantity);

        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.likesCount = 0;
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    private void validateStockQuantity(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (stockQuantity < quantity) {
            throw new CoreException(ErrorType.INSUFFICIENT_STOCK, "재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public Long getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public long getLikesCount() {
        return likesCount;
    }

    public void increaseLikeCount() {
        this.likesCount++;
    }

    public void decreaseLikeCount() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }
}

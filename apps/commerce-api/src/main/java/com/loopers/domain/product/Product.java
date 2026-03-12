package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.UUID;

public record Product(
        UUID id,
        String name,
        Integer price,
        Integer stock,
        String description,
        UUID categoryId,
        UUID brandId,
        Integer likeCount,
        ZonedDateTime deletedAt
) {

    public Product {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        if (categoryId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카테고리 ID는 필수입니다.");
        }
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (likeCount == null || likeCount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 이상이어야 합니다.");
        }
    }

    public Product(String name, Integer price, Integer stock, String description, UUID categoryId, UUID brandId) {
        this(null, name, price, stock, description, categoryId, brandId, 0, null);
    }

    public Product decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        int nextStock = stock - quantity;
        if (nextStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new Product(id, name, price, nextStock, description, categoryId, brandId, likeCount, deletedAt);
    }

    public Product increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        return new Product(id, name, price, stock + quantity, description, categoryId, brandId, likeCount, deletedAt);
    }

    public Product decreaseLikeCount() {
        int nextLikeCount = Math.max(likeCount - 1, 0);
        return new Product(id, name, price, stock, description, categoryId, brandId, nextLikeCount, deletedAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

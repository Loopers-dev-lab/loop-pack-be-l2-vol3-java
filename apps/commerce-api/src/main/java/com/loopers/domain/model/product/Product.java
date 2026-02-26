package com.loopers.domain.model.product;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {

    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final Long id;
    private final Long brandId;
    private final ProductName name;
    private final ProductPricing pricing;
    private final Stock stock;
    private final int likeCount;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;

    public static Product create(Long brandId, ProductName name, Price price, Price salePrice,
                                 Stock stock, String description) {
        LocalDateTime now = LocalDateTime.now();
        return new Product(null, brandId, name, ProductPricing.of(price, salePrice), stock, 0,
                validateDescription(description), now, now, null);
    }

    public static Product reconstitute(ProductData data) {
        return new Product(data.id(), data.brandId(), data.name(),
                ProductPricing.of(data.price(), data.salePrice()), data.stock(),
                data.likeCount(), data.description(),
                data.createdAt(), data.updatedAt(), data.deletedAt());
    }

    public Product update(ProductName name, Price price, Price salePrice, Stock stock, String description) {
        return new Product(this.id, this.brandId, name, ProductPricing.of(price, salePrice), stock, this.likeCount,
                validateDescription(description), this.createdAt, LocalDateTime.now(), this.deletedAt);
    }

    public Product delete() {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 상품입니다.");
        }
        return withDeletedAt(LocalDateTime.now());
    }

    public Product decreaseStock(int quantity) {
        return withStock(this.stock.decrease(quantity));
    }

    public Product increaseStock(int quantity) {
        return withStock(this.stock.increase(quantity));
    }

    public Product increaseLikeCount() {
        return new Product(this.id, this.brandId, this.name, this.pricing, this.stock,
                this.likeCount + 1, this.description, this.createdAt, this.updatedAt, this.deletedAt);
    }

    public Product decreaseLikeCount() {
        if (this.likeCount <= 0) {
            throw new IllegalStateException("좋아요 수는 0 미만이 될 수 없습니다.");
        }
        return new Product(this.id, this.brandId, this.name, this.pricing, this.stock,
                this.likeCount - 1, this.description, this.createdAt, this.updatedAt, this.deletedAt);
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public Price getPrice() {
        return this.pricing.getPrice();
    }

    public Price getSalePrice() {
        return this.pricing.getSalePrice();
    }

    public boolean isOnSale() {
        return this.pricing.isOnSale();
    }

    public int getDiscountRate() {
        return this.pricing.getDiscountRate();
    }

    public boolean isSoldOut() {
        return this.stock.getValue() == 0;
    }

    private Product withStock(Stock newStock) {
        return new Product(this.id, this.brandId, this.name, this.pricing, newStock,
                this.likeCount, this.description, this.createdAt, LocalDateTime.now(), this.deletedAt);
    }

    private Product withDeletedAt(LocalDateTime newDeletedAt) {
        return new Product(this.id, this.brandId, this.name, this.pricing, this.stock,
                this.likeCount, this.description, this.createdAt, this.updatedAt, newDeletedAt);
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}

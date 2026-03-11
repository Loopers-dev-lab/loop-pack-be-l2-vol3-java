package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 *  Product 도메인
 */
public class Product {

    private final Long id;
    private final Long refBrandId;

    private String name;
    private Money price;
    private Integer stock;
    private Integer likeCount;

    private Product(Long id, String name, Long refBrandId, Money price, Integer stock, Integer likeCount) {
        this.id = id;
        this.name = name;
        this.refBrandId = refBrandId;
        this.price = price;
        this.stock = stock;
        this.likeCount = likeCount;
    }

    public static Product create(Long id, String name, Long refBrandId, Integer price, Integer stock, Integer likeCount) {
        validateName(name);
        validateBrandId(refBrandId);
        validateStock(stock);
        validateLike(likeCount);

        return new Product(id, name, refBrandId, new Money(price), stock, likeCount);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.PRODUCT_NAME_REQUIRED);
        }
    }

    private static void validateBrandId(Long refBrand) {
        if (refBrand == null || refBrand <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.BRAND_ID_INVALID);
        }
    }

    private static void validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.STOCK_INVALID);
        }
    }

    private static void validateLike(Integer likeCount) {
        if (likeCount == null || likeCount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.LIKE_COUNT_INVALID);
        }
    }

    public boolean hasEnoughStock(Integer requiredQuantity) {
        return this.stock >= requiredQuantity;
    }

    public void decreaseStock(Integer quantity) {
        validateQuantity(quantity);
        if (!hasEnoughStock(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }

    public void increaseLikeCount() {
        this.likeCount += 1;
    }

    public void decreaseLikeCount() {
        if(this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.LIKE_COUNT_NEGATIVE);
        }
        this.likeCount -= 1;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.QUANTITY_MUST_BE_POSITIVE);
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getRefBrandId() {
        return refBrandId;
    }

    public Money getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getLikeCount() { return likeCount; }
}

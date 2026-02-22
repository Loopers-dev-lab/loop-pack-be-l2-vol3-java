package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Product {
    private final Long id;
    private final Long brandId;
    private final String name;
    private final Money price;
    private int stock;

    private Product(Long id, Long brandId, String name, Money price, int stock) {
        validateName(name);
        validateStock(stock);
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static Product create(Long brandId, String name, Money price, int stock) {
        return new Product(null, brandId, name, price, stock);
    }

    public static Product of(Long id, Long brandId, String name, Money price, int stock) {
        return new Product(id, brandId, name, price, stock);
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 현재 재고: " + this.stock);
        }
        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
    }

    private void validateStock(int stock) {
        if (stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}

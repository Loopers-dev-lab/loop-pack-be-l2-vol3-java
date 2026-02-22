package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Option {
    private final Long id;
    private final Long productId;
    private final String name;
    private final Money additionalPrice;
    private int stock;

    private Option(Long id, Long productId, String name, Money additionalPrice, int stock) {
        validateProductId(productId);
        validateName(name);
        validateStock(stock);
        this.id = id;
        this.productId = productId;
        this.name = name;
        this.additionalPrice = additionalPrice != null ? additionalPrice : Money.zero();
        this.stock = stock;
    }

    public static Option create(Long productId, String name, Money additionalPrice, int stock) {
        return new Option(null, productId, name, additionalPrice, stock);
    }

    public static Option of(Long id, Long productId, String name, Money additionalPrice, int stock) {
        return new Option(id, productId, name, additionalPrice, stock);
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

    public boolean isSoldOut() {
        return this.stock <= 0;
    }

    public void updateStock(int newStock) {
        validateStock(newStock);
        this.stock = newStock;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션명은 필수입니다.");
        }
    }

    private void validateStock(int stock) {
        if (stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}

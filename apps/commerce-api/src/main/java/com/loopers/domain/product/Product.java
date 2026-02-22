package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Product {
    private final Long id;
    private final Long brandId;
    private String name;
    private Money basePrice;
    private boolean deleted;

    private Product(Long id, Long brandId, String name, Money basePrice, boolean deleted) {
        validateName(name);
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.basePrice = basePrice;
        this.deleted = deleted;
    }

    public static Product create(Long brandId, String name, Money basePrice) {
        return new Product(null, brandId, name, basePrice, false);
    }

    public static Product of(Long id, Long brandId, String name, Money basePrice, boolean deleted) {
        return new Product(id, brandId, name, basePrice, deleted);
    }

    public void update(String name, Money basePrice) {
        validateName(name);
        this.name = name;
        this.basePrice = basePrice;
    }

    public void delete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
    }
}

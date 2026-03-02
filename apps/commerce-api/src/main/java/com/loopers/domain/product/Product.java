package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "base_price", nullable = false))
    private Money basePrice;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    private Product(Long brandId, String name, Money basePrice, boolean deleted) {
        validateName(name);
        this.brandId = brandId;
        this.name = name;
        this.basePrice = basePrice;
        this.deleted = deleted;
    }

    public static Product create(Long brandId, String name, Money basePrice) {
        return new Product(brandId, name, basePrice, false);
    }

    public void update(String name, Money basePrice) {
        validateName(name);
        this.name = name;
        this.basePrice = basePrice;
    }

    @Override
    public void delete() {
        this.deleted = true;
    }

    @Override
    public void restore() {
        this.deleted = false;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
    }
}

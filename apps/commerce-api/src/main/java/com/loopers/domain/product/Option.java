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
@Table(name = "options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Option extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "additional_price", nullable = false))
    private Money additionalPrice;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    private Option(Long productId, String name, Money additionalPrice, int stock, boolean deleted) {
        validateProductId(productId);
        validateName(name);
        validateStock(stock);
        this.productId = productId;
        this.name = name;
        this.additionalPrice = additionalPrice != null ? additionalPrice : Money.zero();
        this.stock = stock;
        this.deleted = deleted;
    }

    public static Option create(Long productId, String name, Money additionalPrice, int stock) {
        return new Option(productId, name, additionalPrice, stock, false);
    }

    @Override
    public void delete() {
        this.deleted = true;
    }

    @Override
    public void restore() {
        this.deleted = false;
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

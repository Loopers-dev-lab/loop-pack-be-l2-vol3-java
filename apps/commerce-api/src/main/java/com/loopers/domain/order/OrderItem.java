package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Quantity;
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
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OrderItem extends BaseEntity {

    // order_id FK - @JoinColumn으로 관리되므로 읽기 전용
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    // 원본 상품 추적용 FK
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    // 주문 시점 수량 (BR-O02: 1 이상)
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    // 주문 시점 스냅샷 (BR-O05)
    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false, updatable = false)
    private String brandName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    public OrderItem(Long productId, Quantity quantity, String productName, String brandName, Money price) {
        validateProductId(productId);
        validateProductName(productName);
        validateBrandName(brandName);
        validatePrice(price);

        this.productId = productId;
        this.quantity = quantity;
        this.productName = productName;
        this.brandName = brandName;
        this.price = price;
    }

    @Override
    protected void guard() {
        validateProductId(this.productId);
        validateProductName(this.productName);
        validateBrandName(this.brandName);
        validatePrice(this.price);
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validateBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Money price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
    }
}

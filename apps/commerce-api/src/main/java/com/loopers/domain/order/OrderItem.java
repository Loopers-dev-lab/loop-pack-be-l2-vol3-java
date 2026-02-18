package com.loopers.domain.order;

import com.loopers.domain.Quantity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private int productPrice;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected OrderItem() {}

    OrderItem(Order order, Long productId, String productName, Money productPrice, String brandName, int quantity) {
        validate(order, productId, productName, productPrice, brandName);
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice.amount();
        this.brandName = brandName;
        this.quantity = new Quantity(quantity).value();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Money getProductPrice() { return new Money(productPrice); }
    public String getBrandName() { return brandName; }
    public Quantity getQuantity() { return new Quantity(quantity); }
    public ZonedDateTime getCreatedAt() { return createdAt; }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

    private void validate(Order order, Long productId, String productName, Money productPrice, String brandName) {
        if (order == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문은 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 필수입니다.");
        }
        if (productPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
    }
}

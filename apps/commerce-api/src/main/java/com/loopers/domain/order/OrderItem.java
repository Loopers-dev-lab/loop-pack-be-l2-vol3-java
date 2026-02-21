package com.loopers.domain.order;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.common.vo.Quantity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    protected OrderItem() {}

    private OrderItem(Long productId, String productName, String brandName, int unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.unitPrice = new Money(unitPrice);
        this.quantity = new Quantity(quantity);
    }

    public static OrderItem create(Long productId, String productName, String brandName, int unitPrice, int quantity) {
        return new OrderItem(productId, productName, brandName, unitPrice, quantity);
    }

    public int getLineTotal() {
        return this.unitPrice.toInt() * this.quantity.toInt();
    }

    public Long getId() {
        return this.id;
    }

    public Long getProductId() {
        return this.productId;
    }

    public String getProductName() {
        return this.productName;
    }

    public String getBrandName() {
        return this.brandName;
    }

    public int getUnitPrice() {
        return this.unitPrice.toInt();
    }

    public int getQuantity() {
        return this.quantity.toInt();
    }
}

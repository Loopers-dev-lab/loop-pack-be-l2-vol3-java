package com.loopers.domain.order;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.common.vo.Quantity;

/**
 * OrderItem Domain POJO (순수 도메인 객체)
 * JPA 의존성 없음
 */
public class OrderItem {

    private Long id;
    private Long productId;
    private String productName;
    private String brandName;
    private Money unitPrice;
    private Quantity quantity;

    protected OrderItem() {}

    private OrderItem(Long productId, String productName, String brandName, int unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.unitPrice = new Money(unitPrice);
        this.quantity = new Quantity(quantity);
    }

    public static OrderItem snapshot(Long productId, String productName, String brandName, int unitPrice, int quantity) {
        return new OrderItem(productId, productName, brandName, unitPrice, quantity);
    }

    /**
     * DB에서 읽어온 데이터로 도메인 객체 재구성
     * Infrastructure 계층에서만 호출
     */
    public static OrderItem reconstitute(Long id, Long productId, String productName,
                                          String brandName, int unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.productId = productId;
        item.productName = productName;
        item.brandName = brandName;
        item.unitPrice = new Money(unitPrice);
        item.quantity = new Quantity(quantity);
        return item;
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

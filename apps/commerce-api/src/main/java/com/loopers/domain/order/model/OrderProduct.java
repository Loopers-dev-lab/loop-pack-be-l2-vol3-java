package com.loopers.domain.order.model;

import com.loopers.domain.order.vo.Quantity;
import com.loopers.domain.product.vo.Money;
import com.loopers.domain.product.vo.ProductName;
import lombok.Getter;

@Getter
public class OrderProduct {

    private Long id;
    private Long productId;
    private ProductName productName;
    private Money price;
    private Quantity quantity;

    private OrderProduct(Long productId, ProductName productName, Money price, Quantity quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderProduct create(Long productId, String productName, int price, int quantity) {
        return new OrderProduct(
                productId,
                new ProductName(productName),
                new Money(price),
                new Quantity(quantity)
        );
    }

    public static OrderProduct reconstruct(Long id, Long productId, String productName, int price, int quantity) {
        OrderProduct orderProduct = new OrderProduct(
                productId,
                new ProductName(productName),
                new Money(price),
                new Quantity(quantity)
        );
        orderProduct.id = id;
        return orderProduct;
    }
}

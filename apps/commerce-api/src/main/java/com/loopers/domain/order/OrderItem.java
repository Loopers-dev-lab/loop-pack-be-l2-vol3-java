package com.loopers.domain.order;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String productThumbnailUrl;

    @AttributeOverride(name = "amount", column = @Column(name = "product_price", nullable = false))
    private Money productPrice;

    @Column(nullable = false)
    private Long quantity;

    public static OrderItem create(Long productId, String productName, String productThumbnailUrl, Money productPrice, Long quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.INVALID_ORDER_ITEM_QUANTITY);
        }

        OrderItem orderItem = new OrderItem();
        orderItem.productId = productId;
        orderItem.productName = productName;
        orderItem.productThumbnailUrl = productThumbnailUrl;
        orderItem.productPrice = productPrice;
        orderItem.quantity = quantity;
        return orderItem;
    }

    public Money calculateSubtotal() {
        return productPrice.multiply(quantity);
    }

    void setOrder(Order order) {
        this.order = order;
    }
}

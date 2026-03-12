package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_price", nullable = false)
    private int orderPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Embedded
    private ProductSnapshot productSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderItemStatus status;

    private OrderItemModel(Long productId, int orderPrice, int quantity,
                           ProductSnapshot productSnapshot) {
        this.productId = productId;
        this.orderPrice = orderPrice;
        this.quantity = quantity;
        this.productSnapshot = productSnapshot;
        this.status = OrderItemStatus.ORDERED;
    }

    public static OrderItemModel create(Long productId, int orderPrice, int quantity,
                                        String productName, String brandName) {
        validateProductId(productId);
        validateOrderPrice(orderPrice);
        validateQuantity(quantity);
        validateProductName(productName);
        validateBrandName(brandName);
        return new OrderItemModel(productId, orderPrice, quantity,
                new ProductSnapshot(productName, brandName));
    }

    public String getProductName() {
        return productSnapshot.getProductName();
    }

    public String getBrandName() {
        return productSnapshot.getBrandName();
    }

    public void cancel() {
        if (this.status == OrderItemStatus.CANCELLED) {
            throw new CoreException(OrderErrorCode.ALREADY_CANCELLED_ITEM);
        }
        this.status = OrderItemStatus.CANCELLED;
    }

    public static int calculateTotalPrice(List<OrderItemModel> items) {
        return items.stream()
                .mapToInt(item -> item.getOrderPrice() * item.getQuantity())
                .sum();
    }

    void assignOrder(OrderModel order) {
        this.order = order;
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수값입니다.");
        }
    }

    private static void validateOrderPrice(int orderPrice) {
        if (orderPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }

    private static void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수값입니다.");
        }
    }

    private static void validateBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수값입니다.");
        }
    }
}

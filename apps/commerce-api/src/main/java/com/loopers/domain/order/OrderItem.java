package com.loopers.domain.order;

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
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "order_item")
@Getter
public class OrderItem {

    private static final int QUANTITY_MIN = 1;
    private static final int QUANTITY_MAX = 9_999_999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected OrderItem() {
    }

    private OrderItem(Long productId, String productName, BigDecimal price, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItem create(Long productId, String productName,
                                   BigDecimal price, Integer quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validatePrice(price);
        validateQuantity(quantity);
        return new OrderItem(productId, productName, price, quantity);
    }

    void assignOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getOrderPrice() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다");
        }
    }

    private static void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다");
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 필수입니다");
        }
        if (quantity < QUANTITY_MIN) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다");
        }
        if (quantity > QUANTITY_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 9,999,999 이하여야 합니다");
        }
    }
}

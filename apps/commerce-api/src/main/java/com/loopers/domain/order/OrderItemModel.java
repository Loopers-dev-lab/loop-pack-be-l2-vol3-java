package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * 주문 항목. 주문 시점의 상품 스냅샷(이름·가격)을 보존한다.
 */
@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class OrderItemModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "option_id")
    private Long optionId;

    private OrderItemModel(OrderModel order, Long productId, String productNameSnapshot,
            BigDecimal priceSnapshot, int quantity, Long optionId) {
        this.order = order;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
        this.optionId = optionId;
    }

    /**
     * 스냅샷과 수량·옵션으로 주문 항목을 생성한다.
     *
     * @param snapshot 상품 스냅샷(상품 ID, 이름, 가격)
     * @param quantity 수량 (1 이상)
     * @param optionId 옵션 ID (null 가능)
     * @return 주문 항목 (order는 호출 측에서 set)
     */
    public static OrderItemModel of(ProductSnapshot snapshot, Quantity quantity, Long optionId) {
        if (snapshot == null) {
            throw new IllegalArgumentException("스냅샷은 null일 수 없습니다.");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        return new OrderItemModel(
                null,
                snapshot.productId(),
                snapshot.productName(),
                snapshot.price().value(),
                quantity.value(),
                optionId);
    }

    void setOrder(OrderModel order) {
        this.order = order;
    }
}

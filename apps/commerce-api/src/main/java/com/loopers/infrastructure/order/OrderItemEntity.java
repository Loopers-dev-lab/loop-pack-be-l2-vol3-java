package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id")
    @Getter
    private Long productId;

    @Column(nullable = false)
    @Getter
    private int quantity;

    @Column(name = "snapshot_product_name", nullable = false)
    @Getter
    private String snapshotProductName;

    @Column(name = "snapshot_price", nullable = false)
    @Getter
    private int snapshotPrice;

    @Column(name = "snapshot_brand_name", nullable = false)
    @Getter
    private String snapshotBrandName;

    protected OrderItemEntity() {}

    public OrderItemEntity(OrderEntity order, OrderItem item) {
        this.order = order;
        this.productId = item.productId();
        this.quantity = item.quantity();
        this.snapshotProductName = item.snapshotProductName();
        this.snapshotPrice = item.snapshotPrice();
        this.snapshotBrandName = item.snapshotBrandName();
    }

    public OrderItem toDomain() {
        return new OrderItem(
                id,
                order.getId(),
                productId,
                quantity,
                snapshotProductName,
                snapshotPrice,
                snapshotBrandName
        );
    }
}

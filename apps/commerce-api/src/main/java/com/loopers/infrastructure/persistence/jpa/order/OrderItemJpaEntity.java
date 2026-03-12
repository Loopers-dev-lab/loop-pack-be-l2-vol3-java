package com.loopers.infrastructure.persistence.jpa.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 주문 항목 JPA 엔티티.
 * Infrastructure Layer에 위치하며 영속성을 담당.
 */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    protected OrderItemJpaEntity() {}

    public OrderItemJpaEntity(OrderJpaEntity order, Long productId, String productName,
            Integer quantity, Long priceSnapshot) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceSnapshot = priceSnapshot;
    }

    public Long getId() {
        return id;
    }

    public OrderJpaEntity getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getPriceSnapshot() {
        return priceSnapshot;
    }

    void setOrder(OrderJpaEntity order) {
        this.order = order;
    }
}

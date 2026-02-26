package com.loopers.infrastructure.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "snapshot_id")
    private OrderSnapshotJpaEntity snapshot;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String address;

    private String deliveryRequest;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(nullable = false)
    private int totalAmount;

    @Column(nullable = false)
    private int discountAmount;

    @Column(nullable = false)
    private int paymentAmount;

    @Column(nullable = false)
    private String status;

    private LocalDate desiredDeliveryDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected OrderJpaEntity() {}

    public OrderJpaEntity(Long id, String userId, List<OrderItemJpaEntity> items,
                          OrderSnapshotJpaEntity snapshot, String receiverName, String address,
                          String deliveryRequest, String paymentMethod,
                          int totalAmount, int discountAmount, int paymentAmount,
                          String status, LocalDate desiredDeliveryDate,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.snapshot = snapshot;
        this.receiverName = receiverName;
        this.address = address;
        this.deliveryRequest = deliveryRequest;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.paymentAmount = paymentAmount;
        this.status = status;
        this.desiredDeliveryDate = desiredDeliveryDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}

package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Long totalAmount;

    protected Order() {}

    private Order(Long userId, Long totalAmount) {
        this.userId = userId;
        this.status = Status.ORDERED;
        this.totalAmount = totalAmount;
    }

    public static Order create(Long userId, Long totalAmount) {
        return new Order(userId, totalAmount);
    }

    public enum Status {
        ORDERED
    }
}

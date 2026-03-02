package com.loopers.infrastructure.order.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.model.Orders;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

@Getter
@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class OrderEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int totalPrice;

    protected OrderEntity() {}

    private OrderEntity(Long memberId, int totalPrice) {
        this.memberId = memberId;
        this.totalPrice = totalPrice;
    }

    public static OrderEntity toEntity(Orders orders) {
        return new OrderEntity(orders.getMemberId(), orders.getTotalPrice().value());
    }

    public Orders toModel() {
        return Orders.reconstruct(this.getId(), this.memberId, this.totalPrice, List.of());
    }
}

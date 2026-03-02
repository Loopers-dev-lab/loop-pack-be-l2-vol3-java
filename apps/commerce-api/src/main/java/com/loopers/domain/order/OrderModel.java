package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> orderItems = new ArrayList<>();

    public OrderModel(Long userId, List<OrderItemModel> orderItems) {
        validate(userId, orderItems);
        this.userId = userId;
        this.totalAmount = 0L;
        orderItems.forEach(this::addOrderItem);
    }

    private void addOrderItem(OrderItemModel orderItem) {
        orderItem.setOrder(this);
        this.orderItems.add(orderItem);
        this.totalAmount += orderItem.getLineTotalAmount();
    }

    private void validate(Long userId, List<OrderItemModel> orderItems) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 최소 1개 이상이어야 합니다.");
        }
    }
}

package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_member_id", columnList = "member_id"),
    @Index(name = "idx_orders_member_created_at", columnList = "member_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "original_total_price", nullable = false)
    private int originalTotalPrice;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "coupon_issue_id")
    private Long couponIssueId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    public static Order create(Long memberId, List<ItemSnapshot> snapshots) {
        return create(memberId, snapshots, null, 0);
    }

    public static Order create(Long memberId, List<ItemSnapshot> snapshots,
                                Long couponIssueId, int discountAmount) {
        if (snapshots == null || snapshots.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Order order = new Order();
        order.memberId = memberId;
        order.status = OrderStatus.CREATED;
        for (ItemSnapshot s : snapshots) {
            order.items.add(new OrderItem(
                s.productId(), s.productName(), s.productPrice(), s.brandName(), s.quantity()
            ));
        }
        order.originalTotalPrice = order.items.stream().mapToInt(OrderItem::getSubtotal).sum();
        if (discountAmount < 0 || discountAmount > order.originalTotalPrice) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "할인 금액이 유효하지 않습니다. (할인: " + discountAmount + ", 주문 금액: " + order.originalTotalPrice + ")");
        }
        order.discountAmount = discountAmount;
        order.totalPrice = order.originalTotalPrice - discountAmount;
        order.couponIssueId = couponIssueId;
        return order;
    }

    public record ItemSnapshot(
        Long productId, String productName, int productPrice, String brandName, int quantity
    ) {}

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }
}

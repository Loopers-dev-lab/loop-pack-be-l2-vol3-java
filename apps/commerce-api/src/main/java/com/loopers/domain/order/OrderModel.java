package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.vo.OrderId;
import com.loopers.infrastructure.jpa.converter.OrderIdConverter;
import com.loopers.infrastructure.jpa.converter.RefMemberIdConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    @Convert(converter = OrderIdConverter.class)
    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private OrderId orderId;

    @Convert(converter = RefMemberIdConverter.class)
    @Column(name = "ref_member_id", nullable = false)
    private RefMemberId refMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "ref_user_coupon_id")
    private Long refUserCouponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> orderItems = new ArrayList<>();

    private OrderModel(Long memberId, List<OrderItemModel> items, BigDecimal discountAmount, Long refUserCouponId) {
        this.orderId = OrderId.generate();
        this.refMemberId = new RefMemberId(memberId);
        this.status = OrderStatus.PENDING;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.refUserCouponId = refUserCouponId;
        items.forEach(this::addOrderItem);
    }

    public static OrderModel create(Long memberId, List<OrderItemModel> items, BigDecimal discountAmount, Long refUserCouponId) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 비어 있습니다.");
        }
        return new OrderModel(memberId, items, discountAmount, refUserCouponId);
    }

    public static OrderModel create(Long memberId, List<OrderItemModel> items) {
        return create(memberId, items, BigDecimal.ZERO, null);
    }

    public void pay() {
        this.status.validateTransition(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELED) {
            return;
        }
        this.status.validateTransition(OrderStatus.CANCELED);
        this.status = OrderStatus.CANCELED;
    }

    public boolean isOwner(Long memberId) {
        return this.refMemberId.value().equals(memberId);
    }

    public BigDecimal getOriginalAmount() {
        return orderItems.stream()
                .map(OrderItemModel::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getFinalAmount() {
        BigDecimal original = getOriginalAmount();
        BigDecimal final_ = original.subtract(discountAmount);
        return final_.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : final_;
    }

    private void addOrderItem(OrderItemModel item) {
        this.orderItems.add(item);
        item.setOrder(this);
    }
}

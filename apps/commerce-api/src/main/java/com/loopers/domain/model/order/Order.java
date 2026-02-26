package com.loopers.domain.model.order;

import com.loopers.domain.model.common.AggregateRoot;
import com.loopers.domain.model.order.event.OrderCancelledEvent;
import com.loopers.domain.model.user.UserId;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class Order extends AggregateRoot {

    private final Long id;
    private final UserId userId;
    private final List<OrderItem> items;
    private final OrderSnapshot snapshot;
    private final DeliveryInfo deliveryInfo;
    private final OrderAmount orderAmount;
    private final OrderStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Order(Long id, UserId userId, List<OrderItem> items, OrderSnapshot snapshot,
                  DeliveryInfo deliveryInfo, OrderAmount orderAmount,
                  OrderStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.snapshot = snapshot;
        this.deliveryInfo = deliveryInfo;
        this.orderAmount = orderAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Order create(UserId userId, List<OrderLine> orderLines,
                               DeliveryInfo deliveryInfo, PaymentMethod paymentMethod,
                               Money discountAmount) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (orderLines == null || orderLines.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderItem> items = orderLines.stream()
                .map(line -> OrderItem.create(line.productId(), line.quantity(), line.unitPrice()))
                .toList();

        String snapshotData = orderLines.stream()
                .map(line -> line.productName() + ":" + line.unitPrice().getValue())
                .collect(Collectors.joining(","));
        OrderSnapshot snapshot = OrderSnapshot.create(snapshotData + ",");

        Money totalAmount = calculateTotalAmount(items);
        OrderAmount orderAmount = OrderAmount.of(paymentMethod, totalAmount, discountAmount);
        LocalDateTime now = LocalDateTime.now();

        return new Order(null, userId, items, snapshot, deliveryInfo, orderAmount,
                OrderStatus.PAYMENT_COMPLETED, now, now);
    }

    public static Order reconstitute(OrderData data) {
        return new Order(data.id(), data.userId(), data.items(), data.snapshot(),
                data.deliveryInfo(), data.orderAmount(), data.status(),
                data.createdAt(), data.updatedAt());
    }

    public Order cancel() {
        if (!isCancellable()) {
            throw new IllegalStateException("현재 상태에서는 주문을 취소할 수 없습니다. 현재 상태: " + status.getDescription());
        }

        Order cancelled = withStatus(OrderStatus.CANCELLED);

        List<OrderCancelledEvent.CancelledItem> cancelledItems = this.items.stream()
                .map(item -> new OrderCancelledEvent.CancelledItem(item.getProductId(), item.getQuantity()))
                .toList();
        cancelled.registerEvent(new OrderCancelledEvent(this.id, cancelledItems));

        return cancelled;
    }

    public Order updateDeliveryAddress(String newAddress) {
        if (!status.isAddressChangeable()) {
            throw new IllegalStateException("현재 상태에서는 배송지를 변경할 수 없습니다. 현재 상태: " + status.getDescription());
        }
        return new Order(this.id, this.userId, this.items, this.snapshot,
                this.deliveryInfo.withAddress(newAddress), this.orderAmount,
                this.status, this.createdAt, LocalDateTime.now());
    }

    public boolean isCancellable() {
        return status.isCancellable();
    }

    private Order withStatus(OrderStatus newStatus) {
        return new Order(this.id, this.userId, this.items, this.snapshot,
                this.deliveryInfo, this.orderAmount,
                newStatus, this.createdAt, LocalDateTime.now());
    }

    private static Money calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::calculateAmount)
                .reduce(Money.zero(), Money::add);
    }

    // Delegate getters
    public String getReceiverName() { return deliveryInfo.getReceiverName(); }
    public String getAddress() { return deliveryInfo.getAddress(); }
    public String getDeliveryRequest() { return deliveryInfo.getDeliveryRequest(); }
    public LocalDate getDesiredDeliveryDate() { return deliveryInfo.getDesiredDeliveryDate(); }
    public PaymentMethod getPaymentMethod() { return orderAmount.getPaymentMethod(); }
    public Money getTotalAmount() { return orderAmount.getTotalAmount(); }
    public Money getDiscountAmount() { return orderAmount.getDiscountAmount(); }
    public Money getPaymentAmount() { return orderAmount.getPaymentAmount(); }
}

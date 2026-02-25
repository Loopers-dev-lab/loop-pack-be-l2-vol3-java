package com.loopers.domain.order;

import com.loopers.domain.common.vo.Address;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order Domain POJO (순수 도메인 객체)
 * JPA 의존성 없음
 */
public class Order {

    private Long id;
    private Long userId;
    private String orderNumber;
    private List<OrderItem> items = new ArrayList<>();
    private Money subtotalAmount;
    private Money discountAmount;
    private Money pointUsedAmount;
    private Money shippingFee;
    private Money totalAmount;
    private OrderStatus status;
    private String ordererName;
    private String ordererPhone;
    private String receiverName;
    private String receiverPhone;
    private Address shippingAddress;
    private Long paymentId;
    private String paymentMethod;
    private ZonedDateTime orderedAt;
    private ZonedDateTime expiresAt;
    private ZonedDateTime canceledAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected Order() {}

    private Order(Long userId, String orderNumber, List<OrderItem> items,
                  String ordererName, String ordererPhone,
                  String receiverName, String receiverPhone,
                  String zipCode, String addressLine1, String addressLine2) {
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.items = new ArrayList<>(items);
        this.ordererName = ordererName;
        this.ordererPhone = ordererPhone;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.shippingAddress = new Address(zipCode, addressLine1, addressLine2);
        this.subtotalAmount = new Money(items.stream().mapToInt(OrderItem::getLineTotal).sum());
        this.discountAmount = Money.zero();
        this.pointUsedAmount = Money.zero();
        this.shippingFee = Money.zero();
        this.totalAmount = this.subtotalAmount;
        this.status = OrderStatus.PENDING;
        this.expiresAt = ZonedDateTime.now().plusMinutes(30);
    }

    public static Order create(Long userId, String orderNumber, List<OrderItem> items,
                                String ordererName, String ordererPhone,
                                String receiverName, String receiverPhone,
                                String zipCode, String addressLine1, String addressLine2) {
        return new Order(userId, orderNumber, items, ordererName, ordererPhone,
                receiverName, receiverPhone, zipCode, addressLine1, addressLine2);
    }

    /**
     * DB에서 읽어온 데이터로 도메인 객체 재구성
     * Infrastructure 계층에서만 호출
     */
    public static Order reconstitute(Long id, Long userId, String orderNumber, List<OrderItem> items,
                                      int subtotalAmount, int discountAmount, int pointUsedAmount,
                                      int shippingFee, int totalAmount, OrderStatus status,
                                      String ordererName, String ordererPhone,
                                      String receiverName, String receiverPhone, Address shippingAddress,
                                      Long paymentId, String paymentMethod,
                                      ZonedDateTime orderedAt, ZonedDateTime expiresAt, ZonedDateTime canceledAt,
                                      ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        Order order = new Order();
        order.id = id;
        order.userId = userId;
        order.orderNumber = orderNumber;
        order.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        order.subtotalAmount = new Money(subtotalAmount);
        order.discountAmount = new Money(discountAmount);
        order.pointUsedAmount = new Money(pointUsedAmount);
        order.shippingFee = new Money(shippingFee);
        order.totalAmount = new Money(totalAmount);
        order.status = status;
        order.ordererName = ordererName;
        order.ordererPhone = ordererPhone;
        order.receiverName = receiverName;
        order.receiverPhone = receiverPhone;
        order.shippingAddress = shippingAddress;
        order.paymentId = paymentId;
        order.paymentMethod = paymentMethod;
        order.orderedAt = orderedAt;
        order.expiresAt = expiresAt;
        order.canceledAt = canceledAt;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        order.deletedAt = deletedAt;
        return order;
    }

    public void confirm(Long paymentId, String paymentMethod) {
        validatePending();
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
        this.orderedAt = ZonedDateTime.now();
    }

    public void cancel() {
        validatePending();
        this.status = OrderStatus.CANCELED;
        this.canceledAt = ZonedDateTime.now();
    }

    public void expire() {
        validatePending();
        this.status = OrderStatus.EXPIRED;
    }

    public void applyDiscount(int discountAmount, int pointUsedAmount, int shippingFee) {
        validatePending();
        int total = this.subtotalAmount.toInt() - discountAmount - pointUsedAmount + shippingFee;
        if (total < 0) {
            throw new CoreException(OrderErrorType.DISCOUNT_EXCEEDS_TOTAL);
        }
        this.discountAmount = new Money(discountAmount);
        this.pointUsedAmount = new Money(pointUsedAmount);
        this.shippingFee = new Money(shippingFee);
        this.totalAmount = new Money(total);
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(OrderErrorType.NOT_OWNER);
        }
    }

    private void validatePending() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(OrderErrorType.INVALID_ORDER_STATUS);
        }
    }

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public String getOrderNumber() {
        return this.orderNumber;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public int getSubtotalAmount() {
        return this.subtotalAmount.toInt();
    }

    public int getDiscountAmount() {
        return this.discountAmount.toInt();
    }

    public int getPointUsedAmount() {
        return this.pointUsedAmount.toInt();
    }

    public int getShippingFee() {
        return this.shippingFee.toInt();
    }

    public int getTotalAmount() {
        return this.totalAmount.toInt();
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    public String getOrdererName() {
        return this.ordererName;
    }

    public String getOrdererPhone() {
        return this.ordererPhone;
    }

    public String getReceiverName() {
        return this.receiverName;
    }

    public String getReceiverPhone() {
        return this.receiverPhone;
    }

    public Address getShippingAddress() {
        return this.shippingAddress;
    }

    public String getZipCode() {
        return this.shippingAddress.getZipCode();
    }

    public String getAddressLine1() {
        return this.shippingAddress.getAddressLine1();
    }

    public String getAddressLine2() {
        return this.shippingAddress.getAddressLine2();
    }

    public Long getPaymentId() {
        return this.paymentId;
    }

    public String getPaymentMethod() {
        return this.paymentMethod;
    }

    public ZonedDateTime getOrderedAt() {
        return this.orderedAt;
    }

    public ZonedDateTime getExpiresAt() {
        return this.expiresAt;
    }

    public ZonedDateTime getCanceledAt() {
        return this.canceledAt;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}

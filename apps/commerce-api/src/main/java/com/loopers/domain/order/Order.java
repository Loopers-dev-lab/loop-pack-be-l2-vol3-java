package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Address;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "subtotal_amount", nullable = false))
    private Money subtotalAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "point_used_amount", nullable = false))
    private Money pointUsedAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "shipping_fee", nullable = false))
    private Money shippingFee;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "orderer_name", nullable = false)
    private String ordererName;

    @Column(name = "orderer_phone", nullable = false)
    private String ordererPhone;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;

    @Embedded
    @AttributeOverride(name = "zipCode", column = @Column(name = "zip_code", nullable = false))
    @AttributeOverride(name = "addressLine1", column = @Column(name = "address_line1", nullable = false))
    @AttributeOverride(name = "addressLine2", column = @Column(name = "address_line2"))
    private Address shippingAddress;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "ordered_at")
    private ZonedDateTime orderedAt;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    @Column(name = "canceled_at")
    private ZonedDateTime canceledAt;

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
        this.discountAmount = new Money(discountAmount);
        this.pointUsedAmount = new Money(pointUsedAmount);
        this.shippingFee = new Money(shippingFee);
        int total = this.subtotalAmount.toInt() - discountAmount - pointUsedAmount + shippingFee;
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

    public Long getUserId() {
        return this.userId;
    }

    public String getOrderNumber() {
        return this.orderNumber;
    }

    public List<OrderItem> getItems() {
        return this.items;
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
}

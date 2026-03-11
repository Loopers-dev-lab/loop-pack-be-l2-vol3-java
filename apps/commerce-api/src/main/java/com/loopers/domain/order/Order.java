package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 *  Order 도메인 (애그리거트 루트)
 */
public class Order {

    private final Long id;
    private final Long refUserId;
    private final Long refCouponId;

    private OrderStatus status;
    private Money originalPrice;  // 쿠폰 적용 전 금액
    private Money discountAmount; // 할인 금액
    private Money totalPrice;     // 최종 결제 금액
    private ZonedDateTime orderDt;

    // 애그리거트 내부 엔티티 컬렉션
    private final List<OrderItem> items = new ArrayList<>();
    private final List<OrderStatusHistory> histories = new ArrayList<>();

    private Order(Long id, Long refUserId, Long refCouponId, OrderStatus status,
                  Money originalPrice, Money discountAmount, Money totalPrice, ZonedDateTime orderDt) {
        this.id = id;
        this.refUserId = refUserId;
        this.refCouponId = refCouponId;
        this.status = status;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.orderDt = orderDt;
    }

    public static Order create(Long id, Long refUserId, OrderStatus status, Integer totalPrice, ZonedDateTime orderDt) {
        validateRefUserId(refUserId);
        validateOrderDt(orderDt);

        Money price = new Money(totalPrice);
        return new Order(id, refUserId, null, status, price, Money.ZERO, price, orderDt);
    }

    /**
     * 저장소에서 복원할 때 사용하는 팩토리 메서드
     * items와 histories를 함께 받아서 복원한다.
     */
    public static Order restore(Long id, Long refUserId, Long refCouponId, OrderStatus status,
                                Integer originalPrice, Integer discountAmount, Integer totalPrice,
                                ZonedDateTime orderDt, List<OrderItem> items, List<OrderStatusHistory> histories) {
        validateRefUserId(refUserId);
        validateOrderDt(orderDt);

        Order order = new Order(id, refUserId, refCouponId, status,
            new Money(originalPrice), new Money(discountAmount), new Money(totalPrice), orderDt);
        if (items != null) {
            order.items.addAll(items);
        }
        if (histories != null) {
            order.histories.addAll(histories);
        }
        return order;
    }

    /**
     * 주문 애그리거트 생성 팩토리 (쿠폰 미적용)
     */
    public static Order place(Long userId, List<OrderItemSpec> itemSpecs, ZonedDateTime now) {
        return place(userId, itemSpecs, null, Money.ZERO, now);
    }

    /**
     * 주문 애그리거트 생성 팩토리 (쿠폰 적용)
     * - 주문자, 주문 아이템 스펙, 쿠폰 ID, 할인 금액, 주문 시각을 받아 애그리거트를 구성한다.
     */
    public static Order place(Long userId, List<OrderItemSpec> itemSpecs, Long couponId, Money discount, ZonedDateTime now) {
        validateRefUserId(userId);
        if (itemSpecs == null || itemSpecs.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_ITEMS_EMPTY);
        }
        validateOrderDt(now);

        Money originalTotal = itemSpecs.stream()
            .map(spec -> spec.price().multiply(spec.quantity()))
            .reduce(Money.ZERO, Money::add);

        Money finalTotal = originalTotal.subtract(discount);

        Order order = new Order(
            null,
            userId,
            couponId,
            OrderStatus.ORDERED,
            originalTotal,
            discount,
            finalTotal,
            now
        );

        itemSpecs.forEach(spec ->
            order.addItem(spec.productId(), spec.price(), spec.quantity())
        );
        order.recordStatusChange(OrderStatus.ORDERED, now);

        return order;
    }

    private static void validateRefUserId(Long refUserId) {
        if (refUserId == null || refUserId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.USER_ID_INVALID);
        }
    }

    private static void validateOrderDt(ZonedDateTime orderDt) {
        if (orderDt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_DT_REQUIRED);
        }
    }

    /**
     * 애그리거트 내부에 주문 아이템 추가
     */
    public void addItem(Long productId, Money unitPrice, int quantity) {
        this.items.add(OrderItem.create(
            null,
            this.id, // 새 주문의 경우 null일 수 있지만, 인프라에서 매핑 시 채워진다
            productId,
            quantity,
            unitPrice.value()
        ));
    }

    /**
     * 주문 상태 변경 + 이력 기록
     */
    public void recordStatusChange(OrderStatus newStatus, ZonedDateTime changedAt) {
        if (newStatus == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_STATUS_REQUIRED);
        }
        if (changedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_STATUS_CHANGE_DT_REQUIRED);
        }
        this.status = newStatus;
        this.histories.add(OrderStatusHistory.create(
            null,
            this.id,
            newStatus,
            changedAt
        ));
    }

    public void cancel() {
        if (this.status != OrderStatus.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.CANCEL_ONLY_WHEN_COMPLETED);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getRefUserId() {
        return refUserId;
    }

    public Long getRefCouponId() {
        return refCouponId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getOriginalPrice() {
        return originalPrice;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public ZonedDateTime getOrderDt() {
        return orderDt;
    }

    /**
     * 애그리거트 내부 컬렉션 조회용 (불변 뷰 반환)
     */
    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<OrderStatusHistory> getHistories() {
        return List.copyOf(histories);
    }
}


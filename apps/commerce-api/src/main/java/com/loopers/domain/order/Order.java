package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문 도메인 엔티티 (Aggregate Root).
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class Order {

    private Long id;
    private Long userId;
    private List<OrderItem> items;
    private Money totalPrice;
    private OrderStatus status;
    private ZonedDateTime createdAt;

    // 할인 관련 필드
    private Money originalAmount;
    private Money couponDiscount;
    private Money pointDiscount;
    private Long couponId;

    private Order() {}

    /**
     * 새 주문 생성.
     *
     * @param userId 사용자 ID
     * @param items 주문 항목 목록 (1개 이상)
     * @return 생성된 Order
     * @throws CoreException 주문 항목이 비어있는 경우
     */
    public static Order create(Long userId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        Money total = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);

        Order order = new Order();
        order.userId = userId;
        order.items = new ArrayList<>(items);
        order.totalPrice = total;
        order.originalAmount = total;
        order.couponDiscount = Money.ZERO;
        order.pointDiscount = Money.ZERO;
        order.couponId = null;
        order.status = OrderStatus.CREATED;
        order.createdAt = ZonedDateTime.now();
        return order;
    }

    /**
     * 할인이 적용된 주문 생성.
     *
     * @param userId 사용자 ID
     * @param items 주문 항목 목록
     * @param couponId 사용된 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param pointDiscount 포인트 할인액
     * @return 생성된 Order
     */
    public static Order createWithDiscount(Long userId, List<OrderItem> items,
            Long couponId, Money couponDiscount, Money pointDiscount) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        Money originalAmount = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);

        Money totalDiscount = couponDiscount.add(pointDiscount);
        Money finalAmount = originalAmount.subtract(totalDiscount);

        Order order = new Order();
        order.userId = userId;
        order.items = new ArrayList<>(items);
        order.originalAmount = originalAmount;
        order.couponDiscount = couponDiscount;
        order.pointDiscount = pointDiscount;
        order.totalPrice = finalAmount;
        order.couponId = couponId;
        order.status = OrderStatus.CREATED;
        order.createdAt = ZonedDateTime.now();
        return order;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static Order reconstitute(Long id, Long userId, List<OrderItem> items,
            Money totalPrice, OrderStatus status, ZonedDateTime createdAt) {
        Order order = new Order();
        order.id = id;
        order.userId = userId;
        order.items = new ArrayList<>(items);
        order.totalPrice = totalPrice;
        order.originalAmount = totalPrice;
        order.couponDiscount = Money.ZERO;
        order.pointDiscount = Money.ZERO;
        order.couponId = null;
        order.status = status;
        order.createdAt = createdAt;
        return order;
    }

    /**
     * DB에서 복원 (할인 정보 포함).
     */
    public static Order reconstituteWithDiscount(Long id, Long userId, List<OrderItem> items,
            Money totalPrice, Money originalAmount, Money couponDiscount, Money pointDiscount,
            Long couponId, OrderStatus status, ZonedDateTime createdAt) {
        Order order = new Order();
        order.id = id;
        order.userId = userId;
        order.items = new ArrayList<>(items);
        order.totalPrice = totalPrice;
        order.originalAmount = originalAmount;
        order.couponDiscount = couponDiscount;
        order.pointDiscount = pointDiscount;
        order.couponId = couponId;
        order.status = status;
        order.createdAt = createdAt;
        return order;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public Money getOriginalAmount() {
        return originalAmount;
    }

    public Money getCouponDiscount() {
        return couponDiscount;
    }

    public Money getPointDiscount() {
        return pointDiscount;
    }

    public Long getCouponId() {
        return couponId;
    }
}

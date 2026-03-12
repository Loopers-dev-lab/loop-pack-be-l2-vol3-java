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
}

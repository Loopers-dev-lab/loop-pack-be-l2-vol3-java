package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 주문 항목 도메인 엔티티.
 * 주문 시점의 상품 정보 스냅샷을 포함.
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class OrderItem {

    private Long id;
    private Long productId;
    private String productName;       // 스냅샷
    private int quantity;
    private Money priceSnapshot;      // 주문 시점 단가

    private OrderItem() {}

    /**
     * 새 주문 항목 생성.
     *
     * @param productId 상품 ID
     * @param productName 상품명 (스냅샷)
     * @param quantity 수량 (1 이상)
     * @param price 단가 (스냅샷)
     * @return 생성된 OrderItem
     * @throws CoreException 수량이 0 이하인 경우
     */
    public static OrderItem create(Long productId, String productName, int quantity, Money price) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }

        OrderItem item = new OrderItem();
        item.productId = productId;
        item.productName = productName;
        item.quantity = quantity;
        item.priceSnapshot = price;
        return item;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static OrderItem reconstitute(Long id, Long productId, String productName,
            int quantity, Money priceSnapshot) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.productId = productId;
        item.productName = productName;
        item.quantity = quantity;
        item.priceSnapshot = priceSnapshot;
        return item;
    }

    /**
     * 소계 계산 (단가 × 수량).
     *
     * @return 소계 금액
     */
    public Money getSubtotal() {
        return priceSnapshot.multiply(quantity);
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getPriceSnapshot() {
        return priceSnapshot;
    }
}

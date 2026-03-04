package com.loopers.domain.order.model;

import com.loopers.domain.product.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Orders {

    private Long id;
    private Long memberId;
    private Money totalPrice;
    private Money discountAmount;
    private Long userCouponId;
    private List<OrderProduct> orderProducts;

    private Orders(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자 정보는 필수입니다.");
        }
        this.memberId = memberId;
        this.orderProducts = new ArrayList<>();
        this.discountAmount = new Money(0);
    }

    public static Orders create(Long memberId, List<OrderProduct> orderProducts, int discountAmount, Long userCouponId) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 최소 1개 이상이어야 합니다.");
        }
        Orders orders = new Orders(memberId);
        orders.orderProducts.addAll(orderProducts);
        orders.discountAmount = new Money(discountAmount);
        orders.userCouponId = userCouponId;
        orders.totalPrice = orders.calculateTotalPrice();
        return orders;
    }

    public static Orders reconstruct(Long id, Long memberId, int totalPrice, int discountAmount, Long userCouponId, List<OrderProduct> orderProducts) {
        Orders orders = new Orders(memberId);
        orders.id = id;
        orders.totalPrice = new Money(totalPrice);
        orders.discountAmount = new Money(discountAmount);
        orders.userCouponId = userCouponId;
        orders.orderProducts.addAll(orderProducts);
        return orders;
    }

    private Money calculateTotalPrice() {
        Money subtotal = orderProducts.stream()
                .map(op -> op.getPrice().multiply(op.getQuantity().value()))
                .reduce(Money::add)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 최소 1개 이상이어야 합니다."));
        return subtotal.subtract(discountAmount);
    }

    public List<OrderProduct> getOrderProducts() {
        return Collections.unmodifiableList(orderProducts);
    }
}

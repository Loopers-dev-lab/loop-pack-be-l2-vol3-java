package com.loopers.application.order;

import java.util.List;
import java.util.Map;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 주문 생성을 위한 커맨드 객체.
 * <p>
 * 컨트롤러의 주문 요청을 {@link Order} 도메인 객체로 변환하는 중간 역할을 한다.
 * 현재 Cart 도메인이 존재하지 않으며, 추후 장바구니 도메인이 도입되면 이름 변경을 검토한다.
 */
public record Cart(
        Long userId,
        List<CartItem> cartItems
) {

    public Order toOrder(Map<Long, Product> products) {
        List<OrderItem> items = cartItems.stream()
                .map(item -> {
                    Product product = products.get(item.productId());
                    if (product == null) {
                        throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
                    }
                    return item.toOrderItem(product);
                })
                .toList();
        return Order.create(userId, items);
    }

    /**
     * 개별 주문 항목 정보를 담는 커맨드 객체.
     */
    public record CartItem(
            Long productId,
            Long quantity
    ) {

        public OrderItem toOrderItem(Product product) {
            return OrderItem.create(
                    product.getId(),
                    product.getName().getValue(),
                    product.getThumbnailUrl().getValue(),
                    product.getPrice(),
                    quantity
            );
        }
    }
}

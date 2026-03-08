package com.loopers.application.order;

import java.util.List;
import java.util.Map;

import com.loopers.domain.order.Cart;
import com.loopers.domain.order.Cart.CartItem;
import com.loopers.domain.product.Product;

public record PlaceOrderCommand(
        Long userId,
        List<OrderItemCommand> items,
        Long ownedCouponId
) {

    public Cart toCart(Map<Long, Product> products) {
        List<CartItem> cartItems = items.stream()
                .map(item -> {
                    Product product = products.get(item.productId());
                    return new CartItem(
                            product.getId(),
                            product.getName().getValue(),
                            product.getThumbnailUrl().getValue(),
                            product.getPrice(),
                            item.quantity()
                    );
                })
                .toList();
        return new Cart(userId, cartItems);
    }

    public List<Long> getProductIds() {
        return items.stream()
                .map(OrderItemCommand::productId)
                .distinct()
                .toList();
    }

    public record OrderItemCommand(
            Long productId,
            Long quantity
    ) {

    }
}

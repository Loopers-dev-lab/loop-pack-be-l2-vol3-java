package com.loopers.domain.order;

import java.util.List;

import com.loopers.domain.shared.Money;

/**
 * 주문 생성을 위한 도메인 입력 객체.
 * <p>
 * {@link OrderItem} 생성에 필요한 상품 스냅샷 정보를 보유한다.
 */
public record Cart(
        Long userId,
        List<CartItem> cartItems
) {

    public record CartItem(
            Long productId,
            String productName,
            String productThumbnailUrl,
            Money productPrice,
            Long quantity
    ) {

        public Money totalPrice() {
            return productPrice.multiply(quantity);
        }
    }
}

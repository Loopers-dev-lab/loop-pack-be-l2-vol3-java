package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartInfo {
    private final List<CartItemInfo> items;
    private final Money totalAmount;

    @Getter
    @Builder
    public static class CartItemInfo {
        private final Long cartItemId;
        private final Long productId;
        private final String productName;
        private final Long optionId;
        private final String optionName;
        private final Money unitPrice;
        private final int quantity;
        private final Money totalPrice;
        private final boolean orderable;

        public static CartItemInfo of(CartItem cartItem, Product product, Option option) {
            Money unitPrice = product.getBasePrice().add(option.getAdditionalPrice());
            Money totalPrice = unitPrice.multiply(cartItem.getQuantity());
            boolean orderable = !option.isSoldOut() && option.getStock() >= cartItem.getQuantity();

            return CartItemInfo.builder()
                    .cartItemId(cartItem.getId())
                    .productId(product.getId())
                    .productName(product.getName())
                    .optionId(option.getId())
                    .optionName(option.getName())
                    .unitPrice(unitPrice)
                    .quantity(cartItem.getQuantity())
                    .totalPrice(totalPrice)
                    .orderable(orderable)
                    .build();
        }
    }

    public static CartInfo of(List<CartItemInfo> items) {
        Money totalAmount = items.stream()
                .filter(CartItemInfo::isOrderable)
                .map(CartItemInfo::getTotalPrice)
                .reduce(Money.zero(), Money::add);

        return CartInfo.builder()
                .items(items)
                .totalAmount(totalAmount)
                .build();
    }
}

package com.loopers.application.order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.discount.CouponDiscount;
import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.Cart;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class PlaceOrderUseCase {

    private final OrderService orderService;
    private final ProductService productService;
    private final OwnedCouponService ownedCouponService;

    @Transactional
    public Long execute(PlaceOrderCommand command) {
        List<Long> productIds = command.getProductIds();
        Map<Long, Product> products = productService.getActiveProductsByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
        command.items().stream()
                .sorted(Comparator.comparing(PlaceOrderCommand.OrderItemCommand::productId))
                .forEach(item -> productService.deductStock(item.productId(), item.quantity()));

        Cart cart = command.toCart(products);
        Money orderTotal = Money.sum(cart.cartItems(), Cart.CartItem::totalPrice);
        CouponDiscount couponResult = applyCoupon(command, orderTotal);

        Order order = orderService.create(cart, couponResult.discountAmount(), couponResult.ownedCouponId());
        return order.getId();
    }

    private CouponDiscount applyCoupon(PlaceOrderCommand command, Money orderTotal) {
        if (command.ownedCouponId() == null) {
            return CouponDiscount.NONE;
        }
        return ownedCouponService.applyCoupon(command.ownedCouponId(), command.userId(), orderTotal);
    }
}

package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponApplyResult;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSpec;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo order(OrderCommand command) {
        List<OrderItemSpec> itemSpecs = prepareOrderItems(command.productQuantities());
        Money originalPrice = calculateTotalPrice(itemSpecs);

        CouponApplyResult couponResult = couponService.applyToOrder(
            command.couponId(), command.userId(), originalPrice
        );

        Order savedOrder = orderService.placeOrder(
            command.userId(), itemSpecs, couponResult.couponId(), couponResult.discountAmount()
        );

        return OrderInfo.from(savedOrder);
    }

    private List<OrderItemSpec> prepareOrderItems(Map<Long, Integer> productQuantities) {
        List<Long> productIds = new ArrayList<>(productQuantities.keySet());
        List<Product> products = productService.getByIds(productIds);

        products.forEach(product ->
            productService.decreaseStock(product.getId(), productQuantities.get(product.getId()))
        );

        return products.stream()
            .map(product -> new OrderItemSpec(
                product.getId(),
                product.getPrice(),
                productQuantities.get(product.getId())
            ))
            .toList();
    }

    private Money calculateTotalPrice(List<OrderItemSpec> itemSpecs) {
        return itemSpecs.stream()
            .map(spec -> spec.price().multiply(spec.quantity()))
            .reduce(Money.ZERO, Money::add);
    }
}

package com.loopers.application.order;

import com.loopers.application.coupon.CouponService;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.order.OrderItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;
    private final IssuedCouponService issuedCouponService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        OrderItemValidator.validate(command.items());

        List<Long> orderedProductIds = command.items().stream().map(OrderItemCommand::productId).toList();
        List<ProductInfo> activeProducts = productService.getActiveProductsByIdsOrThrow(orderedProductIds);

        Map<Long, ProductInfo> productMap = activeProducts.stream().collect(Collectors.toMap(ProductInfo::id, p -> p));
        List<OrderItemSnapshot> orderItemSnapshots = command.items().stream()
                                                   .map(item -> {
                                                       ProductInfo p = productMap.get(item.productId());
                                                       return new OrderItemSnapshot(p.id(), p.name(), p.price(), item.quantity());
                                                   })
                                                   .toList();

        long discountAmount = 0L;
        boolean hasDiscountCoupon = command.issuedCouponId() != null;
        if (hasDiscountCoupon) {
            IssuedCouponInfo issuedCoupon = issuedCouponService.getUsableBy(command.issuedCouponId(), command.userId());
            Coupon coupon = couponService.findById(issuedCoupon.couponId());
            long originalAmount = orderItemSnapshots.stream().mapToLong(OrderItemSnapshot::lineAmount).sum();
            coupon.validateApplicable(originalAmount);
            discountAmount = coupon.calculateDiscount(originalAmount);
        }

        productService.decreaseStock(command.items());
        if (!hasDiscountCoupon) {
            return orderService.placeOrder(command.userId(), orderItemSnapshots);
        }

        issuedCouponService.use(command.issuedCouponId(), command.userId());
        return orderService.placeOrder(command.userId(), orderItemSnapshots, discountAmount, command.issuedCouponId());
    }
}

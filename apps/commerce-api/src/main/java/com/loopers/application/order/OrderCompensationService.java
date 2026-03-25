package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderCompensationService {

    private final OrderService orderService;
    private final IssuedCouponService issuedCouponService;
    private final ProductService productService;

    @Transactional
    public void compensate(Long orderId) {
        OrderInfo order = orderService.getOrderById(orderId);
        orderService.markOrderFailed(orderId);

        if (order.issuedCouponId() != null) {
            issuedCouponService.restore(order.issuedCouponId(), order.userId());
        }

        List<OrderItemInfo> items = orderService.getOrderItems(orderId);
        productService.restoreStock(items);
    }
}

package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        return createOrder(userId, null, itemRequests);
    }

    @Transactional
    public OrderInfo createOrder(Long userId, Long couponId, List<OrderItemRequest> itemRequests) {
        User user = userRepository.findByIdWithLock(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            Product product = productRepository.findByIdWithLock(req.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다"));

            product.decreaseStock(req.quantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem(
                product.getId(),
                product.getName(),
                product.getPrice(),
                req.quantity()
            );
            orderItems.add(orderItem);
        }

        long discountAmount = 0L;
        Long usedCouponId = null;

        if (couponId != null) {
            UserCoupon userCoupon = userCouponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다"));

            if (!userCoupon.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인의 쿠폰만 사용할 수 있습니다");
            }

            CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다"));

            long totalBeforeDiscount = orderItems.stream().mapToLong(OrderItem::totalPrice).sum();
            discountAmount = template.calculateDiscount(totalBeforeDiscount);

            userCoupon.use();
            userCouponRepository.save(userCoupon);
            usedCouponId = couponId;
        }

        Order order = new Order(userId, orderItems, discountAmount, usedCouponId);
        user.deductPoint(order.getFinalPrice());
        userRepository.save(user);

        return OrderInfo.from(orderService.createOrder(order));
    }

    public record OrderItemRequest(Long productId, Integer quantity) {}
}

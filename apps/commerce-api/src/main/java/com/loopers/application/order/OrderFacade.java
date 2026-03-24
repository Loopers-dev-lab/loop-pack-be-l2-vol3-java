package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.*;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final OrderRepository orderRepository;
    private final UserCouponRepository userCouponRepository;
    private final StockPolicy stockPolicy;
    private final OrderAssembler orderAssembler;

    @Transactional
    public String createOrder(Long userId, OrderCommand orderCommand) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        List<Long> productIds = orderCommand.items().stream().map(OrderCommand.Item::productId).toList();
        List<Product> products = productRepository.findAllByIdInWithLock(productIds);

        Set<Long> foundIds = products.stream().map(Product::getId).collect(Collectors.toSet());
        boolean hasNotFound = productIds.stream().anyMatch(id -> !foundIds.contains(id));
        if (hasNotFound) {
            throw new CoreException(ErrorType.NOT_FOUND, "등록되지 않은 상품입니다.");
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> brandIds = products.stream().map(Product::brandId).toList();
        Map<Long, Brand> brandMap = brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        List<OrderLine> orderLines = orderCommand.items().stream().map(item -> new OrderLine(item.productId(), item.quantity())).toList();
        stockPolicy.validate(productMap, orderLines);

        // 쿠폰 존재 및 소유권 검증
        Long userCouponId = orderCommand.userCouponId();
        UserCoupon userCoupon = null;
        if (userCouponId != null) {
            userCoupon = userCouponRepository.findById(userCouponId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        }

        // 재고 차감
        orderCommand.items().forEach(item ->
                productMap.get(item.productId()).decreaseStock(item.quantity())
        );

        List<OrderItem> orderItems = orderAssembler.toOrderItems(orderCommand, productMap, brandMap);

        long discountAmount = 0L;
        if (userCoupon != null) {
            long originalAmount = orderItems.stream()
                    .mapToLong(OrderItem::subtotal)
                    .sum();
            userCoupon.use(userId);
            discountAmount = userCoupon.calculateDiscount(originalAmount);
            userCouponRepository.save(userCoupon);
        }

        Order order = Order.of(user.getId(), orderItems, userCouponId, discountAmount);
        orderRepository.save(order);
        return order.orderId();
    }

    @Transactional(readOnly = true)
    public OrderDetail getDetail(Long userId, String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return OrderDetail.from(order);
    }

    @Transactional(readOnly = true)
    public OrderAdminDetail getAdminDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

        User user = userRepository.findById(order.userId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        return OrderAdminDetail.of(order, user);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderAdminSummary> findAllOrders(PageRequest pageRequest) {
        PageResponse<Order> orderPage = orderRepository.findAll(pageRequest);

        Set<Long> userIds = orderPage.content().stream().map(Order::userId).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return orderPage.map(order -> OrderAdminSummary.of(order, userMap.get(order.userId())));
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> getList(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<Order> orders = orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt);
        return orders.stream().map(OrderSummary::from).toList();
    }
}

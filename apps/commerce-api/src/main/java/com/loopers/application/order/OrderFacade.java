package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Quantity;
import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.users.UserService;
import com.loopers.domain.users.Users;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final UserCouponService userCouponService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderInfo createOrder(String loginId, String password, List<OrderV1Dto.OrderItemRequest> items, Long userCouponId, CardType cardType, String cardNo, boolean updateDefaultCard) {
        if (cardType == null || cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 정보는 필수입니다.");
        }

        Users user = userService.authenticate(loginId, password);

        List<Long> productIds = items.stream().map(OrderV1Dto.OrderItemRequest::productId).toList();
        List<Product> products = productService.getProducts(productIds);

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        List<Brand> brands = productService.getBrands(brandIds);

        Map<Long, Brand> brandMap = brands.stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));
        Map<Long, Quantity> deductionMap = items.stream()
            .collect(Collectors.toMap(OrderV1Dto.OrderItemRequest::productId, item -> new Quantity(item.quantity())));

        long totalAmount = products.stream()
            .mapToLong(p -> p.getPrice() * deductionMap.get(p.getId()).getValue())
            .sum();

        Optional<CouponDiscount> couponDiscount = Optional.empty();
        if (userCouponId != null) {
            CouponDiscount discount = userCouponService.validateAndUse(userCouponId, user.getId(), totalAmount);
            couponDiscount = Optional.of(discount);
        }

        Order order = orderService.createOrder(user.getId(), products, brandMap, deductionMap, userCouponId, couponDiscount);

        eventPublisher.publishEvent(new OrderCreatedEvent(
            order.getId(), user.getId(), order.getFinalPrice(), cardType, cardNo, updateDefaultCard
        ));

        return OrderInfo.from(order);
    }

    // 본인의 주문 목록을 최신순 페이징으로 반환한다.
    public Page<OrderInfo> getOrderList(String loginId, String password, int page, int size) {
        Users user = userService.authenticate(loginId, password);
        return orderService.getOrderList(user.getId(), PageRequest.of(page, size))
            .map(OrderInfo::from);
    }

    /**
     * 주문 단건 상세 조회.
     * OrderService에서 소유권 검증 후, OrderItem 목록을 함께 반환한다.
     */
    public OrderDetailInfo getOrderDetail(String loginId, String password, Long orderId) {
        Users user = userService.authenticate(loginId, password);
        Order order = orderService.getOrder(user.getId(), orderId);
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        return OrderDetailInfo.from(order, items);
    }
}

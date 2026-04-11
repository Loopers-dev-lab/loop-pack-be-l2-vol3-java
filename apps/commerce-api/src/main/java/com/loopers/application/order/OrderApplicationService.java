package com.loopers.application.order;

import com.loopers.application.order.event.OrderCancelledEvent;
import com.loopers.application.order.event.OrderCreatedEvent;
import com.loopers.application.order.event.OrderItemSnapshot;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.cart.Cart;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderPolicy;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderApplicationService {

    private final OrderDomainService orderService;
    private final ProductDomainService productService;
    private final ProductStockDomainService productStockService;
    private final BrandDomainService brandService;
    private final CartDomainService cartService;
    private final CouponDomainService couponDomainService;
    private final CouponIssueDomainService couponIssueDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        Order order = processOrder(command.userId(), command.items(), command.couponId());
        publishOrderCreatedEvent(order);
        return order;
    }

    /**
     * 단일 트랜잭션에서 Cart aggregate(비우기), ProductStock aggregate(재고 차감),
     * Order aggregate(생성)를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 장바구니 기반 주문 시 재고 차감, 주문 생성, 장바구니 비우기를
     * 원자적으로 처리하여 일관성을 보장한다.
     */
    @Transactional
    public Order createOrderFromCart(Long userId) {
        Cart cart = cartService.getCart(userId);
        List<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "장바구니가 비어있습니다.");
        }

        Set<Long> cartProductIds = cartItems.stream()
            .map(CartItem::getProductId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.getByIds(cartProductIds);
        Set<Long> availableProductIds = productMap.keySet();

        if (!availableProductIds.containsAll(cartProductIds)) {
            cartService.removeUnavailableItems(cart, availableProductIds);
            cartItems = cartItems.stream()
                .filter(ci -> availableProductIds.contains(ci.getProductId()))
                .toList();
            if (cartItems.isEmpty()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "장바구니의 모든 상품이 더 이상 존재하지 않습니다.");
            }
        }

        List<CreateOrderCommand.LineItem> lineItems = cartItems.stream()
            .map(ci -> new CreateOrderCommand.LineItem(ci.getProductId(), ci.getQuantity().value()))
            .toList();

        Order order = processOrder(userId, lineItems, null);
        cartService.clearCart(userId);
        publishOrderCreatedEvent(order);
        return order;
    }

    /**
     * 단일 트랜잭션에서 Order aggregate(취소), ProductStock aggregate(재고 복원),
     * CouponIssue aggregate(쿠폰 복원)를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 주문 취소 시 재고 복원과 쿠폰 복원을 원자적으로 처리하여 일관성을 보장한다.
     */
    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderService.getByIdAndUserIdWithItems(orderId, userId);
        order.cancel();

        // 재고 복원 (deadlock 방지를 위해 productId 기준 정렬)
        List<OrderItem> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .toList();
        for (OrderItem item : sortedItems) {
            productStockService.restoreWithLock(item.getProductId(), item.getQuantity().value());
        }

        // 쿠폰 복원
        if (order.getCouponIssueId() != null) {
            couponIssueDomainService.restoreCoupon(order.getCouponIssueId());
        }

        eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), userId, ZonedDateTime.now()));
        return order;
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size) {
        return orderService.getMyOrders(userId, startAt, endAt, page, size);
    }

    @Transactional(readOnly = true)
    public Order getMyOrder(Long userId, Long orderId) {
        return orderService.getByIdAndUserIdWithItems(orderId, userId);
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getAllOrders(int page, int size) {
        return orderService.getAllOrders(page, size);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderService.getByIdWithItems(orderId);
    }

    /**
     * 단일 트랜잭션에서 ProductStock aggregate(재고 차감)와 Order aggregate(생성)를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 재고 차감과 주문 생성은 원자적으로 처리되어야 하며,
     * 분리 시 재고 불일치 또는 유령 주문이 발생할 수 있다.
     */
    private Order processOrder(Long userId, List<CreateOrderCommand.LineItem> lineItems, Long couponId) {
        // 0. 중복 상품 조기 차단 (의도적 이중 검증)
        List<Long> productIds = lineItems.stream()
            .map(CreateOrderCommand.LineItem::productId).toList();
        OrderPolicy.validateNoDuplicateProducts(productIds);

        // 1. deadlock 방지를 위해 productId 기준 정렬
        List<CreateOrderCommand.LineItem> sorted = lineItems.stream()
            .sorted(Comparator.comparing(CreateOrderCommand.LineItem::productId)).toList();

        // 2. 쿠폰 조회 — lock 전에 완료 (상태 변경은 lock 구간에서 수행)
        CouponIssue couponIssue = null;
        Coupon coupon = null;
        if (couponId != null) {
            couponIssue = couponIssueDomainService.getByIdAndUserId(couponId, userId);
            coupon = couponDomainService.getById(couponIssue.getCouponId());
        }

        // ----- lock 구간 시작 -----

        // 3. 재고 차감 (pessimistic lock on product_stocks)
        for (CreateOrderCommand.LineItem item : sorted) {
            productStockService.deductWithLock(item.productId(), item.quantity());
        }

        // 4. 상품 조회 + 삭제 상품 검증 (Product 행 락 없이 일반 조회)
        Set<Long> productIdSet = sorted.stream()
            .map(CreateOrderCommand.LineItem::productId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.getByIds(productIdSet);
        if (productMap.size() != productIdSet.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        // 5. Brand 일괄 조회 (N+1 방지)
        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId).collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        // 6. OrderItemCommand 조립
        List<OrderItemCommand> itemCommands = new ArrayList<>();
        for (CreateOrderCommand.LineItem item : sorted) {
            Product product = productMap.get(item.productId());
            Brand brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND,
                    "브랜드를 찾을 수 없습니다. brandId=" + product.getBrandId());
            }
            itemCommands.add(new OrderItemCommand(
                product.getId(), product.getName(), product.getPrice(),
                brand.getName(), item.quantity()
            ));
        }

        // 7. 총 금액 계산
        Money originalPrice = calculateOriginalPrice(itemCommands);

        // 8. 쿠폰 적용 (검증 + 사용)
        if (couponId != null) {
            coupon.validateApplicable(originalPrice);
            Money discountAmount = coupon.calculateDiscount(originalPrice);
            couponIssueDomainService.useCoupon(couponIssue.getId(), userId);
            return orderService.createOrder(userId, itemCommands, originalPrice, discountAmount, couponId);
        }

        // 9. 주문 생성 (쿠폰 없음)
        return orderService.createOrder(userId, itemCommands);
    }

    private void publishOrderCreatedEvent(Order order) {
        List<OrderItemSnapshot> itemSnapshots = order.getItems().stream()
            .map(item -> new OrderItemSnapshot(item.getProductId(), item.getQuantity().value(), item.getProductPrice().amount()))
            .toList();
        eventPublisher.publishEvent(new OrderCreatedEvent(
            order.getId(), order.getUserId(), itemSnapshots, ZonedDateTime.now()));
    }

    private Money calculateOriginalPrice(List<OrderItemCommand> itemCommands) {
        Money total = new Money(0);
        for (OrderItemCommand cmd : itemCommands) {
            total = total.plus(cmd.productPrice().multiply(cmd.quantity()));
        }
        return total;
    }
}

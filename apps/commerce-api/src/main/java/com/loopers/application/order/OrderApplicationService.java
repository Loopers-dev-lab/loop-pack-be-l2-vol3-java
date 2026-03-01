package com.loopers.application.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.cart.Cart;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderPolicy;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderApplicationService {

    private final OrderDomainService orderService;
    private final ProductDomainService productService;
    private final BrandDomainService brandService;
    private final CartDomainService cartService;

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        return processOrder(command.userId(), command.items());
    }

    /**
     * 단일 트랜잭션에서 Cart aggregate(비우기), Product aggregate(재고 차감),
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

        Order order = processOrder(userId, lineItems);
        cartService.clearCart(userId);
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
     * 단일 트랜잭션에서 Product aggregate(재고 차감)와 Order aggregate(생성)를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 재고 차감과 주문 생성은 원자적으로 처리되어야 하며,
     * 분리 시 재고 불일치 또는 유령 주문이 발생할 수 있다.
     */
    private Order processOrder(Long userId, List<CreateOrderCommand.LineItem> lineItems) {
        // 0. 중복 상품 조기 차단 (의도적 이중 검증)
        // OrderDomainService.createOrder()에도 동일 검증이 존재하나,
        // Application 레벨에서 먼저 차단하여 불필요한 pessimistic lock/재고 차감 DB 호출을 방지한다.
        // 도메인 레벨 검증은 다른 진입점(직접 호출 등)에 대한 안전망으로 유지.
        List<Long> productIds = lineItems.stream()
            .map(CreateOrderCommand.LineItem::productId).toList();
        OrderPolicy.validateNoDuplicateProducts(productIds);

        // 1. deadlock 방지를 위해 productId 기준 정렬
        List<CreateOrderCommand.LineItem> sorted = lineItems.stream()
            .sorted(Comparator.comparing(CreateOrderCommand.LineItem::productId)).toList();

        // 2. 재고 차감 (pessimistic lock) — Map으로 관리
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (CreateOrderCommand.LineItem item : sorted) {
            productMap.put(item.productId(),
                productService.deductStockWithLock(item.productId(), item.quantity()));
        }

        // 3. Brand 일괄 조회 (N+1 방지)
        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId).collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        // 4. OrderItemCommand 조립 — productId 키로 안전하게 조회
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

        // 5. 주문 생성 (도메인 서비스 위임)
        return orderService.createOrder(userId, itemCommands);
    }
}

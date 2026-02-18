package com.loopers.application.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderLineItem;
import com.loopers.domain.order.OrderDomainService;
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
    public Order createOrder(Long userId, List<OrderLineItem> items) {
        return processOrder(userId, items);
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
        List<CartItem> cartItems = cartService.getCartItems(userId);
        if (cartItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "장바구니가 비어있습니다.");
        }

        List<OrderLineItem> items = cartItems.stream()
            .map(ci -> new OrderLineItem(ci.getProductId(), ci.getQuantity().value()))
            .toList();

        Order order = processOrder(userId, items);

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
    private Order processOrder(Long userId, List<OrderLineItem> items) {
        // Sort by productId to prevent deadlocks
        List<OrderLineItem> sortedItems = items.stream()
            .sorted(Comparator.comparing(OrderLineItem::productId))
            .toList();

        // Get products with pessimistic lock and deduct stock
        List<Product> products = new ArrayList<>();
        for (OrderLineItem item : sortedItems) {
            Product product = productService.deductStockWithLock(item.productId(), item.quantity());
            products.add(product);
        }

        // Get brands for snapshots
        Set<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        // Build order item commands
        List<OrderItemCommand> itemCommands = new ArrayList<>();
        for (int i = 0; i < sortedItems.size(); i++) {
            OrderLineItem item = sortedItems.get(i);
            Product product = products.get(i);
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

        // Create order (validation and price calculation handled by OrderDomainService)
        return orderService.createOrder(userId, itemCommands);
    }
}

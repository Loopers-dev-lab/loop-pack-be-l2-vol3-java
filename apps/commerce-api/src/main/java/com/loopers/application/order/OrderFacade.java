package com.loopers.application.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;
    private final CartService cartService;

    @Transactional
    public OrderDetailInfo createOrder(Long userId, List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 하나 이상이어야 합니다.");
        }
        return processOrder(userId, items);
    }

    @Transactional
    public OrderDetailInfo createOrderFromCart(Long userId) {
        List<CartItem> cartItems = cartService.getCartItems(userId);
        if (cartItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "장바구니가 비어있습니다.");
        }

        List<OrderItemRequest> items = cartItems.stream()
            .map(ci -> new OrderItemRequest(ci.getProductId(), ci.getQuantity().value()))
            .toList();

        OrderDetailInfo result = processOrder(userId, items);

        cartService.clearCart(userId);

        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size) {
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        PageResult<Order> orders = orderService.getMyOrders(userId, start, end, page, size);
        return orders.map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailInfo getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderService.getByIdAndUserId(orderId, userId);
        List<OrderItem> orderItems = orderService.getOrderItems(orderId);
        return OrderDetailInfo.from(order, orderItems);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getAllOrders(int page, int size) {
        PageResult<Order> orders = orderService.getAllOrders(page, size);
        return orders.map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailInfo getOrderDetail(Long orderId) {
        Order order = orderService.getById(orderId);
        List<OrderItem> orderItems = orderService.getOrderItems(orderId);
        return OrderDetailInfo.from(order, orderItems);
    }

    private OrderDetailInfo processOrder(Long userId, List<OrderItemRequest> items) {
        // Validate for duplicate product IDs
        Set<Long> uniqueProductIds = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!uniqueProductIds.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
            }
        }

        // Sort by productId to prevent deadlocks
        List<OrderItemRequest> sortedItems = items.stream()
            .sorted(Comparator.comparing(OrderItemRequest::productId))
            .toList();

        // Get products with pessimistic lock and deduct stock
        List<Product> products = new ArrayList<>();
        for (OrderItemRequest item : sortedItems) {
            Product product = productService.getByIdWithLock(item.productId());
            product.deductStock(item.quantity());
            products.add(product);
        }

        // Get brands for snapshots
        Set<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        // Build order item commands and calculate total
        Money totalPrice = new Money(0);
        List<OrderItemCommand> itemCommands = new ArrayList<>();
        for (int i = 0; i < sortedItems.size(); i++) {
            OrderItemRequest item = sortedItems.get(i);
            Product product = products.get(i);
            Brand brand = brandMap.get(product.getBrandId());

            Money itemTotal = product.getPrice().multiply(item.quantity());
            totalPrice = totalPrice.plus(itemTotal);

            itemCommands.add(new OrderItemCommand(
                product.getId(), product.getName(), product.getPrice(),
                brand.getName(), item.quantity()
            ));
        }

        // Create order
        Order order = orderService.createOrder(userId, totalPrice, itemCommands);
        List<OrderItem> orderItems = orderService.getOrderItems(order.getId());
        return OrderDetailInfo.from(order, orderItems);
    }

    public record OrderItemRequest(Long productId, int quantity) {}
}

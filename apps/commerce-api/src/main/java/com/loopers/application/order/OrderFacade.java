package com.loopers.application.order;

import com.loopers.application.product.ProductService;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;

    // Command

    @Transactional
    public OrderInfo createOrder(Long userId, OrderCommand.Place command) {
        var items = command.items();

        Set<Long> productIds = items.stream()
                .map(OrderCommand.PlaceItem::productId)
                .collect(Collectors.toSet());

        if (productIds.size() != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
        }

        Map<Long, Integer> productQuantities = items.stream()
                .collect(Collectors.toMap(
                        OrderCommand.PlaceItem::productId,
                        OrderCommand.PlaceItem::quantity
                ));
        List<Product> products = productService.deductStocks(productQuantities);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderCommand.CreateItem> orderItems = items.stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    return OrderCommand.CreateItem.of(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            item.quantity()
                    );
                })
                .toList();

        Order order = orderService.createOrder(OrderCommand.Create.of(userId, orderItems));
        return OrderInfo.from(order);
    }

    // Query

    @Transactional(readOnly = true)
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "시작일은 종료일 이전이어야 합니다");
        }
        ZonedDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay(ZoneId.systemDefault()) : null;
        ZonedDateTime endDateTime = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()) : null;
        Page<Order> orders = orderService.findOrdersByUserIdAndDateRange(userId, startDateTime, endDateTime, pageable);
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.getOrder(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(Pageable pageable) {
        Page<Order> orders = orderService.findAllOrders(pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }
}

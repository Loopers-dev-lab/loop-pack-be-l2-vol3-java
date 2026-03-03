package com.loopers.application.order;

import com.loopers.application.product.ProductService;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Validated
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;

    // Command

    @Transactional
    public OrderInfo createOrder(Long userId, @Valid OrderRequest.Place request) {
        var items = request.orderItems();

        Set<Long> productIds = items.stream()
                .map(OrderRequest.PlaceItem::productId)
                .collect(Collectors.toSet());

        if (productIds.size() != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
        }

        Map<Long, Integer> productQuantities = items.stream()
                .collect(Collectors.toMap(
                        OrderRequest.PlaceItem::productId,
                        OrderRequest.PlaceItem::quantity
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
        Order order = orderService.findOrderById(orderId, userId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, @Valid OrderRequest.ListByUser request) {
        if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "시작일은 종료일 이전이어야 합니다");
        }
        Page<Order> orders = orderService.findOrdersByUserIdAndDateRange(userId, request.startDateTime(), request.endDateTime(), request.toPageable());
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.findOrderById(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(@Valid OrderRequest.ListAll request) {
        Page<Order> orders = orderService.findAllOrders(request.toPageable());
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }
}

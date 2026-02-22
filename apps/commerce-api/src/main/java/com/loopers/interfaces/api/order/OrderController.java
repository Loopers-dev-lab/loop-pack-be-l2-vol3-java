package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController implements OrderApiSpec {

    private final OrderFacade orderFacade;
    private final OrderService orderService;

    public OrderController(OrderFacade orderFacade, OrderService orderService) {
        this.orderFacade = orderFacade;
        this.orderService = orderService;
    }

    @PostMapping
    @Override
    public ApiResponse<OrderResponse.OrderCreateResponse> createOrder(
            @AuthUser User user,
            @RequestBody OrderRequest.CreateOrderRequest request) {
        List<OrderFacade.OrderItemCommand> commands = request.items().stream()
                .map(item -> new OrderFacade.OrderItemCommand(item.productId(), item.quantity()))
                .toList();

        Order order = orderFacade.createOrder(
                user.getId(), user.getName().getValue(), commands, request.addressId());

        return ApiResponse.success(new OrderResponse.OrderCreateResponse(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getExpiresAt()));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderResponse.OrderListResponse> getOrders(
            @AuthUser User user,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt) {
        ZonedDateTime start = startAt != null ? ZonedDateTime.parse(startAt) : ZonedDateTime.now().minusMonths(3);
        ZonedDateTime end = endAt != null ? ZonedDateTime.parse(endAt) : ZonedDateTime.now();

        List<Order> orders = orderService.getOrders(user.getId(), start, end);

        List<OrderResponse.OrderSummary> summaries = orders.stream()
                .map(o -> new OrderResponse.OrderSummary(
                        o.getId(), o.getOrderNumber(), o.getStatus().name(),
                        o.getTotalAmount(), o.getCreatedAt()))
                .toList();

        return ApiResponse.success(new OrderResponse.OrderListResponse(summaries));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderResponse.OrderDetail> getOrder(
            @AuthUser User user,
            @PathVariable Long orderId) {
        Order order = orderService.getOrder(orderId, user.getId());

        List<OrderResponse.OrderItemDetail> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemDetail(
                        item.getProductName(), item.getBrandName(),
                        item.getUnitPrice(), item.getQuantity(), item.getLineTotal()))
                .toList();

        return ApiResponse.success(new OrderResponse.OrderDetail(
                order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getOrdererName(), order.getOrdererPhone(),
                order.getReceiverName(), order.getReceiverPhone(),
                order.getZipCode(), order.getAddressLine1(), order.getAddressLine2(),
                order.getSubtotalAmount(), order.getDiscountAmount(),
                order.getPointUsedAmount(), order.getShippingFee(), order.getTotalAmount(),
                items, order.getCreatedAt()));
    }

    @DeleteMapping("/{orderId}")
    @Override
    public ApiResponse<Object> cancelOrder(
            @AuthUser User user,
            @PathVariable Long orderId) {
        orderFacade.cancelOrder(orderId, user.getId());
        return ApiResponse.success();
    }
}

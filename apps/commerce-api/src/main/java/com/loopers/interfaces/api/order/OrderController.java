package com.loopers.interfaces.api.order;

import com.loopers.application.order.CancelOrderUseCase;
import com.loopers.application.order.CreateOrderUseCase;
import com.loopers.application.order.OrderQueryUseCase;
import com.loopers.application.order.UpdateDeliveryAddressUseCase;
import com.loopers.domain.model.user.UserId;
import com.loopers.interfaces.api.order.dto.DeliveryAddressUpdateRequest;
import com.loopers.interfaces.api.order.dto.OrderCreateRequest;
import com.loopers.interfaces.api.order.dto.OrderDetailResponse;
import com.loopers.interfaces.api.order.dto.OrderSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderQueryUseCase orderQueryUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final UpdateDeliveryAddressUseCase updateDeliveryAddressUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           OrderQueryUseCase orderQueryUseCase,
                           CancelOrderUseCase cancelOrderUseCase,
                           UpdateDeliveryAddressUseCase updateDeliveryAddressUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderQueryUseCase = orderQueryUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.updateDeliveryAddressUseCase = updateDeliveryAddressUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> createOrder(HttpServletRequest request,
                                            @RequestBody OrderCreateRequest orderCreateRequest) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        createOrderUseCase.createOrder(userId, orderCreateRequest.toCommand());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> getMyOrders(
            HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");

        List<OrderQueryUseCase.OrderSummary> summaries;
        if (startAt != null && endAt != null) {
            summaries = orderQueryUseCase.getMyOrders(userId, startAt, endAt);
        } else {
            summaries = orderQueryUseCase.getMyOrders(userId);
        }

        List<OrderSummaryResponse> orders = summaries.stream()
                .map(OrderSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(HttpServletRequest request,
                                                        @PathVariable Long orderId) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        OrderQueryUseCase.OrderDetail detail = orderQueryUseCase.getOrder(userId, orderId);
        return ResponseEntity.ok(OrderDetailResponse.from(detail));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(HttpServletRequest request,
                                            @PathVariable Long orderId) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        cancelOrderUseCase.cancelOrder(userId, orderId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{orderId}/delivery-address")
    public ResponseEntity<Void> updateDeliveryAddress(HttpServletRequest request,
                                                      @PathVariable Long orderId,
                                                      @RequestBody DeliveryAddressUpdateRequest addressRequest) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        updateDeliveryAddressUseCase.updateDeliveryAddress(userId, orderId, addressRequest.address());
        return ResponseEntity.ok().build();
    }
}

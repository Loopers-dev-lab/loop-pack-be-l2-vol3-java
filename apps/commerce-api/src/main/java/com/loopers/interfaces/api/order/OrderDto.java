package com.loopers.interfaces.api.order;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderDto {

    public record CreateOrderRequest(
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다")
            @Valid
            List<OrderItemRequest> items
    ) {
        public CreateOrderCommand toCommand(Long userId) {
            List<CreateOrderCommand.OrderItemCommand> itemCommands = items.stream()
                    .map(i -> new CreateOrderCommand.OrderItemCommand(i.productId(), i.quantity()))
                    .toList();
            return new CreateOrderCommand(userId, itemCommands);
        }
    }

    public record OrderItemRequest(
            @NotNull(message = "상품 ID는 필수입니다")
            Long productId,
            @Min(value = 1, message = "수량은 1 이상이어야 합니다")
            int quantity
    ) {}

    public record OrderItemResponse(
            Long id,
            Long productId,
            int quantity,
            String snapshotProductName,
            int snapshotPrice,
            String snapshotBrandName
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.id(),
                    item.productId(),
                    item.quantity(),
                    item.snapshotProductName(),
                    item.snapshotPrice(),
                    item.snapshotBrandName()
            );
        }
    }

    public record OrderResponse(
            Long id,
            Long userId,
            String orderNumber,
            ZonedDateTime orderDate,
            String status,
            int totalAmount,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.id(),
                    order.userId(),
                    order.orderNumber(),
                    order.orderDate(),
                    order.status().name(),
                    order.totalAmount(),
                    order.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderListResponse(
            List<OrderResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static OrderListResponse from(Page<Order> pageData) {
            return new OrderListResponse(
                    pageData.getContent().stream().map(OrderResponse::from).toList(),
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }
}

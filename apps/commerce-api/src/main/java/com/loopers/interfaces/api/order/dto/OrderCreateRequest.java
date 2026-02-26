package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.CreateOrderUseCase;

import java.time.LocalDate;
import java.util.List;

public record OrderCreateRequest(
        List<OrderItemRequest> items,
        String receiverName,
        String address,
        String deliveryRequest,
        String paymentMethod,
        LocalDate desiredDeliveryDate
) {
    public CreateOrderUseCase.OrderCommand toCommand() {
        List<CreateOrderUseCase.OrderItemCommand> itemCommands = items.stream()
                .map(item -> new CreateOrderUseCase.OrderItemCommand(item.productId(), item.quantity()))
                .toList();

        return new CreateOrderUseCase.OrderCommand(
                itemCommands, receiverName, address, deliveryRequest, paymentMethod, desiredDeliveryDate
        );
    }

    public record OrderItemRequest(
            Long productId,
            int quantity
    ) {}
}

package com.loopers.application.order;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderFacade {
    private final OrderApplicationService orderService;
    private final ProductApplicationService productService;

    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        orderService.validateItems(command.items());
        List<Long> productIds = command.items().stream()
                                       .map(OrderItemCommand::productId)
                                       .toList();
        List<ProductInfo> products = productService.getActiveProductsByIdsOrThrow(productIds);

        productService.decreaseStock(command.items());

        return orderService.createOrder(command.userId(), products, command.items());
    }
}

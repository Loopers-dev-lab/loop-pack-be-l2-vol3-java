package com.loopers.application.order;

import com.loopers.application.product.ProductService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.order.OrderItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderFacade {
    private final OrderService orderService;
    private final ProductService productService;

    @Transactional
    public OrderInfo createOrder(OrderCreateCommand command) {
        orderService.validateItems(command.items());
        List<Long> productIds = command.items().stream()
                                       .map(OrderItemCommand::productId)
                                       .toList();
        List<ProductInfo> products = productService.getActiveProductsByIdsOrThrow(productIds);
        productService.decreaseStock(command.items());

        Map<Long, ProductInfo> productMap = products.stream()
                                                    .collect(Collectors.toMap(ProductInfo::id, p -> p));
        List<OrderItemSnapshot> snapshots = command.items().stream()
                                                   .map(item -> {
                                                       ProductInfo p = productMap.get(item.productId());
                                                       return new OrderItemSnapshot(p.id(), p.name(), p.price(), item.quantity());
                                                   })
                                                   .toList();
        return orderService.placeOrder(command.userId(), snapshots);
    }
}

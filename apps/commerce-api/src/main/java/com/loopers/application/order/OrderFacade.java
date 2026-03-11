package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSpec;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;

    public OrderInfo order(OrderCommand command) {
        List<Long> productIds = new ArrayList<>(command.productQuantities().keySet());
        List<Product> products = productService.getByIds(productIds);

        products.forEach(product ->
            productService.decreaseStock(product.getId(), command.productQuantities().get(product.getId())));

        List<OrderItemSpec> itemSpecs = products.stream()
            .map(product -> new OrderItemSpec(
                product.getId(),
                product.getPrice(),
                command.productQuantities().get(product.getId())
            ))
            .toList();

        // 도메인 서비스에 주문 애그리거트 생성/저장 위임
        Order savedOrder = orderService.placeOrder(command.userId(), itemSpecs);

        return OrderInfo.from(savedOrder);
    }
}

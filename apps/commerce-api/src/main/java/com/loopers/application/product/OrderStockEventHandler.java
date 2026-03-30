package com.loopers.application.product;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderStockEventHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderEvent.Created event) {
        Order order = orderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

        List<Long> productIds = order.items().stream().map(item -> item.productId()).toList();
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        order.items().forEach(item -> productMap.get(item.productId()).decreaseStock(item.quantity()));
    }
}

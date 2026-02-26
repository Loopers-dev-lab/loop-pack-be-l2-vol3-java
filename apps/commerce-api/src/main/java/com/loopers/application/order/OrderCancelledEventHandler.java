package com.loopers.application.order;

import com.loopers.domain.model.order.event.OrderCancelledEvent;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.repository.ProductRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledEventHandler {

    private final ProductRepository productRepository;

    public OrderCancelledEventHandler(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @EventListener
    public void handle(OrderCancelledEvent event) {
        for (OrderCancelledEvent.CancelledItem item : event.cancelledItems()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
            Product restored = product.increaseStock(item.quantity());
            productRepository.save(restored);
        }
    }
}

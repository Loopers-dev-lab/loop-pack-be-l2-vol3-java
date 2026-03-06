package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderDomainService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public List<OrderLine> prepareOrderLines(List<OrderLineRequest> items) {
        List<OrderLineRequest> sorted = items.stream()
            .sorted((a, b) -> Long.compare(a.productId(), b.productId()))
            .toList();

        List<OrderLine> orderLines = new ArrayList<>();
        for (OrderLineRequest item : sorted) {
            Product product = productRepository.findByIdForUpdate(item.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + item.productId() + "] 상품을 찾을 수 없습니다."));
            product.decreaseStock(item.quantity());
            orderLines.add(new OrderLine(item.productId(), item.quantity(), product.getPrice()));
        }
        return orderLines;
    }

    public Order createOrder(Long memberId, List<OrderLine> orderLines) {
        Order order = Order.create(memberId, orderLines);
        return orderRepository.save(order);
    }

    public Order createOrderWithCoupon(Long memberId, List<OrderLine> orderLines, Long couponId, long discountAmount) {
        Order order = Order.createWithCoupon(memberId, orderLines, couponId, discountAmount);
        return orderRepository.save(order);
    }

    public record OrderLineRequest(Long productId, int quantity) {}
}

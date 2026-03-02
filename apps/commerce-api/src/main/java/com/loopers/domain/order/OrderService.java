package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    public record OrderItemRequest(Long productId, Integer quantity) {}

    @Transactional
    public Order createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        List<OrderItem> orderItems = itemRequests.stream()
                .map(request -> {
                    Product product = productService.decreaseStock(request.productId(), request.quantity());
                    return OrderItem.create(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            request.quantity()
                    );
                })
                .toList();

        Order order = Order.create(userId, orderItems);
        return orderRepository.save(order);
    }

    public Order getById(Long id) {
        return orderRepository.findActiveById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Page<Order> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderRepository.findAllActiveByUserId(userId, pageable);
    }

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAllActive(pageable);
    }
}

package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다"));

            product.decreaseStock(req.quantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem(
                product.getId(),
                product.getName(),
                product.getPrice(),
                req.quantity()
            );
            orderItems.add(orderItem);
        }

        Order order = new Order(userId, orderItems);
        user.deductPoint(order.getTotalPrice());
        userRepository.save(user);

        return OrderInfo.from(orderService.createOrder(order));
    }

    public record OrderItemRequest(Long productId, Integer quantity) {}
}

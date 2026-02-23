package com.loopers.application.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.order.Cart.CartItem;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Long createOrder(Cart cart) {
        List<Long> productIds = cart.cartItems().stream()
                .map(CartItem::productId)
                .distinct()
                .toList();
        Map<Long, Product> products = productRepository.findAllByIdInAndDeletedAtIsNullForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }

        Order order = cart.toOrder(products);
        order.getOrderItems().forEach(item -> {
            Product product = products.get(item.getProductId());
            product.deductStock(item.getQuantity());
        });

        Order saved = orderRepository.save(order);
        return saved.getId();
    }
}

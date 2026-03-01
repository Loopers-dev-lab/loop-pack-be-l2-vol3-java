package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.exception.EmptyOrderItemException;
import com.loopers.domain.order.exception.InvalidOrderStatusTransitionException;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.exception.ProductInsufficientStockException;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderDomainService orderDomainService;

    @Transactional
    @Retryable(
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 30)
    )
    public OrderDto.OrderInfo placeOrder(Long userId, List<OrderDto.OrderLineCommand> items) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        List<OrderItem> orderItems = new ArrayList<>();
        Map<Long, Product> touchedProducts = new LinkedHashMap<>();

        if (items != null) {
            for (OrderDto.OrderLineCommand item : items) {
                if (item == null || item.productId() == null) {
                    throw new IllegalArgumentException("상품 ID는 필수입니다");
                }

                Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

                try {
                    product.decreaseStock(item.quantity());
                } catch (ProductInsufficientStockException e) {
                    throw new CoreException(ErrorType.PRODUCT_INSUFFICIENT_STOCK);
                }
                orderItems.add(OrderItem.createSnapshot(
                    product.getId(), product.getName(), product.getPrice(), item.quantity()
                ));
                touchedProducts.put(product.getId(), product);
            }
        }

        Order order;
        try {
            order = orderDomainService.createOrder(userId, orderItems);
        } catch (EmptyOrderItemException e) {
            throw new CoreException(ErrorType.ORDER_EMPTY_ITEMS);
        }
        Order savedOrder = orderRepository.save(order);

        for (Product touchedProduct : touchedProducts.values()) {
            productRepository.save(touchedProduct);
        }

        return OrderDto.OrderInfo.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderDto.OrderInfo getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        return OrderDto.OrderInfo.from(order);
    }

    @Transactional
    @Retryable(
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 30)
    )
    public OrderDto.OrderInfo cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));

        Map<Long, Product> touchedProducts = new LinkedHashMap<>();
        for (com.loopers.domain.order.OrderItem orderItem : order.getOrderItems()) {
            Product product = productRepository.findById(orderItem.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            touchedProducts.put(product.getId(), product);
        }

        if (!orderDomainService.cancelOrder(order)) {
            return OrderDto.OrderInfo.from(order);
        }

        for (com.loopers.domain.order.OrderItem orderItem : order.getOrderItems()) {
            Product product = touchedProducts.get(orderItem.getProductId());
            product.increaseStock(orderItem.getQuantity());
            productRepository.save(product);
        }

        Order savedOrder = orderRepository.save(order);
        return OrderDto.OrderInfo.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderDto.OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        ZonedDateTime effectiveStart = startAt != null
            ? startAt
            : ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime effectiveEnd = endAt != null ? endAt : ZonedDateTime.now(ZoneOffset.UTC);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("조회 시작일은 종료일보다 이후일 수 없습니다");
        }

        return orderRepository.findByUserIdAndDateRange(userId, effectiveStart, effectiveEnd).stream()
            .map(OrderDto.OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderDto.OrderInfo> getAdminOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
            .map(OrderDto.OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderDto.OrderInfo getAdminOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        return OrderDto.OrderInfo.from(order);
    }
}

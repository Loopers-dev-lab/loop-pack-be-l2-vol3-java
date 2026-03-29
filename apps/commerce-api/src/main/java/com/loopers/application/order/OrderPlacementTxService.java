package com.loopers.application.order;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.exception.CouponNotAvailableException;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.exception.EmptyOrderItemException;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.exception.ProductInsufficientStockException;
import com.loopers.application.order.event.OrderPlacedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderPlacementTxService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderDto.OrderInfo placeOrder(Long userId, List<OrderDto.OrderLineCommand> items, Long couponIssueId) {
        CouponIssue couponIssue = null;
        Coupon couponTemplate = null;
        if (couponIssueId != null) {
            couponIssue = couponIssueRepository.findByIdAndUserId(couponIssueId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
            try {
                couponIssue.use();
            } catch (CouponNotAvailableException e) {
                throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
            }
            couponTemplate = couponRepository.findById(couponIssue.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        }

        List<OrderItem> orderItems = new ArrayList<>();
        Map<Long, Product> touchedProducts = new LinkedHashMap<>();
        Map<Long, Product> productsById = findProductsByIds(items);

        if (items != null) {
            for (OrderDto.OrderLineCommand item : items) {
                Product product = productsById.get(item.productId());
                if (product == null) {
                    throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
                }

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
            order = orderService.createOrder(userId, orderItems);
        } catch (EmptyOrderItemException e) {
            throw new CoreException(ErrorType.ORDER_EMPTY_ITEMS);
        }

        if (couponIssue != null && couponTemplate != null) {
            BigDecimal discountAmount = couponTemplate.calculateDiscount(order.getTotalAmount());
            order.applyDiscount(couponIssue.getId(), discountAmount);
        }

        Order savedOrder = orderRepository.save(order);

        for (Product touchedProduct : touchedProducts.values()) {
            productRepository.save(touchedProduct);
        }
        if (couponIssue != null) {
            couponIssueRepository.save(couponIssue);
        }

        eventPublisher.publishEvent(OrderPlacedEvent.from(savedOrder));

        return OrderDto.OrderInfo.from(savedOrder);
    }

    private Map<Long, Product> findProductsByIds(List<OrderDto.OrderLineCommand> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        Set<Long> productIds = items.stream()
            .map(OrderDto.OrderLineCommand::productId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Product> products = productRepository.findAllByIds(productIds);
        Map<Long, Product> productsById = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (productsById.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
        return productsById;
    }
}

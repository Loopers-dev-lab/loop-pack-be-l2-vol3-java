package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.event.UserActionEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CouponService couponService;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order create(Long userId, Long productId, int quantity, Long userCouponId) {
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (!product.canOrder()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문할 수 없는 상품입니다.");
        }

        Brand brand = brandRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        product.deductStock(quantity);
        productRepository.save(product);

        int originalAmount = product.getPrice() * quantity;

        int discountAmount = 0;
        if (userCouponId != null) {
            discountAmount = couponService.validateAndUse(userId, userCouponId, originalAmount);
        }

        OrderItem item = new OrderItem(productId, product.getName(), brand.getName(), quantity, product.getPrice());
        Order order = new Order(userId, List.of(item), userCouponId, discountAmount);
        Order savedOrder = orderRepository.save(order);

        int finalAmount = originalAmount - discountAmount;
        outboxRepository.save(OutboxEvent.forOrderCreated(savedOrder.getId(), productId, quantity, finalAmount));
        eventPublisher.publishEvent(new UserActionEvent(
            UserActionEvent.EventType.ORDER_CREATED, userId, savedOrder.getId(), null
        ));
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public Order findById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> findByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndPeriod(userId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public List<Order> findAllByPeriod(ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByPeriod(startAt, endAt);
    }

    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public void confirmPayment(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.ORDERED) {
            return;
        }

        order.confirmPayment();
        orderRepository.save(order);
    }

    @Transactional
    public void failPayment(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return;
        }

        order.failPayment();
        orderRepository.save(order);

        List<OrderItem> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .toList();

        for (OrderItem item : sortedItems) {
            Product product = productRepository.findByIdWithLock(item.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            product.restoreStock(item.getQuantity());
            productRepository.save(product);
        }

        if (order.getUserCouponId() != null) {
            UserCoupon userCoupon = couponService.findUserCouponById(order.getUserCouponId());
            userCoupon.restore();
            couponService.saveUserCoupon(userCoupon);
        }
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        List<OrderItem> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .toList();

        for (OrderItem item : sortedItems) {
            Product product = productRepository.findByIdWithLock(item.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            product.restoreStock(item.getQuantity());
            productRepository.save(product);
        }

        order.cancel();
        orderRepository.save(order);
    }
}

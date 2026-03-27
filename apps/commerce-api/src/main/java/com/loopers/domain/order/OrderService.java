package com.loopers.domain.order;

import com.loopers.domain.order.dto.OrderCommand;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderModel createOrder(Long userId, List<OrderCommand.CreateItem> commands) {
        List<OrderItemModel> items = commands.stream()
                .map(cmd -> OrderItemModel.create(
                        cmd.productId(), cmd.price(), cmd.quantity(),
                        cmd.productName(), cmd.brandName()))
                .toList();
        return orderRepository.save(OrderModel.createPendingPayment(userId, items));
    }

    @Transactional
    public void applyDiscount(OrderModel order, int discountAmount) {
        order.applyDiscount(discountAmount);
    }

    @Transactional(readOnly = true)
    public OrderModel getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(OrderErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OrderModel getByIdAndUserId(Long id, Long userId) {
        OrderModel order = getById(id);
        order.validateOwner(userId);
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrdersByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt);
    }

    @Transactional
    public OrderInfo.CancelledItem cancelItem(OrderModel order, Long orderItemId) {
        return OrderInfo.CancelledItem.from(
                order.cancelItem(orderItemId), order);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getOrderItemsByOrderId(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Page<OrderModel> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public OrderModel getByIdWithLock(Long id) {
        return orderRepository.findByIdWithLock(id)
                .orElseThrow(() -> new CoreException(OrderErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public void validateNoPendingPayment(Long userId) {
        if (orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING_PAYMENT)) {
            throw new CoreException(OrderErrorCode.PENDING_PAYMENT_EXISTS);
        }
    }

    @Transactional
    public void completeOrder(Long orderId) {
        OrderModel order = getById(orderId);
        order.completePayment();
    }

    @Transactional
    public OrderInfo.PaymentFailureCancellation cancelByPaymentFailure(Long orderId) {
        OrderModel order = getById(orderId);
        order.cancelByPaymentFailure();
        return new OrderInfo.PaymentFailureCancellation(
                order.getUserId(),
                order.getTotalPrice(),
                order.getItems().stream()
                        .map(item -> new OrderInfo.PaymentFailureCancellation.CancelledItem(
                                item.getProductId(), item.getQuantity()))
                        .toList());
    }
}

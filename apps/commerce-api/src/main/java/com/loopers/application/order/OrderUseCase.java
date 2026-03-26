package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.command.UseCouponCommand;
import com.loopers.application.observability.annotation.LogBusinessSuccess;
import com.loopers.application.outbox.OrderCreatedOutboxMessage;
import com.loopers.application.outbox.OrderPaymentOutboxService;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.event.OrderPaymentCancelRequestEvent;
import com.loopers.application.order.event.OrderPaymentRequestEvent;
import com.loopers.application.payment.PaymentQueryApplicationService;
import com.loopers.application.point.PointApplicationService;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.application.product.dto.OrderProductInfo;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderApplicationService orderApplicationService;
    private final ProductStockApplicationService productStockApplicationService;
    private final BrandApplicationService brandApplicationService;
    private final CouponApplicationService couponApplicationService;
    private final PointApplicationService pointApplicationService;
    private final PaymentQueryApplicationService paymentQueryApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderPaymentOutboxService orderPaymentOutboxService;

    @Transactional
    public Order create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderProductInfo> orderProducts =
                productStockApplicationService.getOrderProducts(command.items());

        List<UUID> brandIds = orderProducts.stream()
                .map(OrderProductInfo::brandId)
                .toList();
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(brandIds);

        Map<UUID, OrderProductInfo> orderProductMap = orderProducts.stream()
                .collect(Collectors.toMap(OrderProductInfo::productId, p -> p));

        List<OrderItem> orderItems = command.items().stream()
                .map(item -> {
                    var p = orderProductMap.get(item.productId());
                    return new OrderItem(
                            p.productId(),
                            item.quantity(),
                            p.productName(),
                            p.productPrice(),
                            brandNames.get(p.brandId())
                    );
                })
                .toList();

        int orderAmount = orderItems.stream().mapToInt(OrderItem::totalPrice).sum();
        int discountAmount = 0;
        if (command.couponId() != null) {
            couponApplicationService.use(new UseCouponCommand(command.couponId(), command.memberId(), orderAmount));
            discountAmount = couponApplicationService.calculateDiscount(command.couponId(), orderAmount);
        }

        int amountAfterCoupon = Math.max(orderAmount - discountAmount, 0);
        if (command.pointAmount() > amountAfterCoupon) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 포인트가 결제 예정 금액을 초과할 수 없습니다.");
        }
        int usedPointAmount = pointApplicationService.use(command.memberId(), command.pointAmount());
        int paymentAmount = amountAfterCoupon - usedPointAmount;

        Order createdOrder = orderApplicationService.create(
                command.memberId(),
                orderItems,
                command.couponId(),
                paymentAmount,
                usedPointAmount
        );
        applicationEventPublisher.publishEvent(
                new OrderPaymentRequestEvent(
                        command.memberId(),
                        createdOrder.id(),
                        command.cardType(),
                        command.cardNo(),
                        createdOrder.totalAmount()
                )
        );
        orderPaymentOutboxService.saveOrderCreated(new OrderCreatedOutboxMessage(
                UUID.randomUUID(),
                createdOrder.id(),
                command.memberId(),
                createdOrder.totalAmount(),
                Instant.now()
        ));
        return createdOrder;
    }

    @Transactional
    public Order cancel(OrderAccessRequest request) {
        Order order = orderApplicationService.getById(request);
        boolean stockWasDeducted = order.isStockDeducted();

        if (order.couponId() != null) {
            couponApplicationService.cancelUse(order.couponId(), order.memberId());
        }

        if (order.usedPointAmount() > 0) {
            pointApplicationService.restore(order.memberId(), order.usedPointAmount());
        }

        if (stockWasDeducted) {
            productStockApplicationService.restoreForOrder(order.items());
        }

        Order cancelled = orderApplicationService.cancel(request);
        if (!stockWasDeducted && cancelled.isStockDeducted()) {
            productStockApplicationService.restoreForOrder(cancelled.items());
        }

        PaymentStatus paymentStatus = paymentQueryApplicationService
                .getPaymentByOrder(cancelled.memberId(), cancelled.id())
                .map(Payment::status)
                .orElse(null);

        if (paymentStatus == PaymentStatus.SUCCEEDED || paymentStatus == PaymentStatus.CANCEL_FAILED) {
            applicationEventPublisher.publishEvent(
                    new OrderPaymentCancelRequestEvent(cancelled.memberId(), cancelled.id())
            );
        }
        return cancelled;
    }
}

package com.loopers.application.process.checkout;

import com.loopers.application.observability.annotation.LogBusinessSuccess;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.outbox.OrderPaymentOutboxService;
import com.loopers.application.process.checkout.event.OrderCancelRequestedEvent;
import com.loopers.application.process.checkout.event.OrderCreateRequestedEvent;
import com.loopers.contract.kafka.OrderCreatedOutboxMessage;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCancelSagaProgress;
import com.loopers.domain.order.OrderCancelSagaProgressRepository;
import com.loopers.domain.order.OrderCreateSagaProgress;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderApplicationService orderApplicationService;
    private final OrderCreatePreparationService orderCreatePreparationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderPaymentOutboxService orderPaymentOutboxService;
    private final OrderCancelSagaProgressRepository orderCancelSagaProgressRepository;
    private final OrderCreateSagaProgressRepository orderCreateSagaProgressRepository;

    @Transactional
    @LogBusinessSuccess(action = "ORDER_CREATE", domain = "order", memberIdArg = "command")
    public Order create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        OrderCreatePreparationService.PreparedOrder prepared = orderCreatePreparationService.prepare(command);

        Order createdOrder = orderApplicationService.create(
                command.memberId(),
                prepared.orderItems(),
                command.couponId(),
                prepared.paymentAmount(),
                prepared.usedPointAmount()
        );
        orderPaymentOutboxService.saveOrderCreated(new OrderCreatedOutboxMessage(
                UUID.randomUUID(),
                createdOrder.id(),
                command.memberId(),
                createdOrder.totalAmount(),
                Instant.now()
        ));
        orderCreateSagaProgressRepository.save(OrderCreateSagaProgress.initialize(
                createdOrder.id(),
                command.memberId(),
                command.couponId(),
                prepared.orderAmount(),
                command.pointAmount(),
                prepared.paymentAmount(),
                command.cardType().name(),
                command.cardNo(),
                command.couponId() != null,
                prepared.usedPointAmount() > 0
        ));
        applicationEventPublisher.publishEvent(
                new OrderCreateRequestedEvent(
                        command.memberId(),
                        createdOrder.id(),
                        command.couponId(),
                        prepared.usedPointAmount(),
                        command.cardType(),
                        command.cardNo(),
                        createdOrder.totalAmount()
                )
        );
        return createdOrder;
    }

    @Transactional
    @LogBusinessSuccess(action = "ORDER_CANCEL", domain = "order", memberIdArg = "request", aggregateIdArg = "request")
    public Order cancel(OrderAccessRequest request) {
        Order cancelPendingOrder = orderApplicationService.requestCancel(request);
        orderCancelSagaProgressRepository.save(OrderCancelSagaProgress.initialize(
                cancelPendingOrder.id(),
                cancelPendingOrder.couponId() != null,
                cancelPendingOrder.usedPointAmount() > 0,
                cancelPendingOrder.isStockDeducted()
        ));
        applicationEventPublisher.publishEvent(new OrderCancelRequestedEvent(cancelPendingOrder.id()));
        return cancelPendingOrder;
    }
}

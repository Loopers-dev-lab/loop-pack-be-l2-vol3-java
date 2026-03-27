package com.loopers.application.event;

import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.event.PaymentRequestedEvent;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.domain.payment.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentRequested(PaymentRequestedEvent event) {
        log.info("PG 결제 요청 처리 - paymentId: {}, orderId: {}", event.paymentId(), event.orderId());
        PaymentCommand.PgRequest pgCommand = new PaymentCommand.PgRequest(
                event.orderNumber(), event.cardType(), event.cardNo(), event.amount(), event.callbackUrl()
        );
        PaymentInfo info = paymentGateway.requestPayment(event.memberId(), pgCommand);

        if (!info.hasTransactionKey()) {
            paymentService.handlePgFailure(event.paymentId());
            orderService.updateOrderStatus(event.orderId(), OrderStatus.PAYMENT_FAILED);
            log.warn("PG 결제 요청 실패 - paymentId: {}", event.paymentId());
            return;
        }

        paymentService.markRequested(event.paymentId(), info.transactionKey());
        orderService.updateOrderStatus(event.orderId(), OrderStatus.PAYMENT_REQUESTED);
        log.info("PG 결제 요청 성공 - paymentId: {}, transactionKey: {}", event.paymentId(), info.transactionKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 성공 이벤트 - orderId: {}, paymentId: {}", event.orderId(), event.paymentId());
        outboxEventRepository.save(OutboxEvent.create(
                OutboxEventType.PAYMENT_COMPLETED,
                event.orderId(),
                toJson(event)
        ));
        log.info("유저 행동 로깅 - action: PAYMENT_SUCCESS, orderId: {}", event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("결제 실패 이벤트 - orderId: {}, paymentId: {}, reason: {}", event.orderId(), event.paymentId(), event.reason());
        log.info("유저 행동 로깅 - action: PAYMENT_FAILED, orderId: {}", event.orderId());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}

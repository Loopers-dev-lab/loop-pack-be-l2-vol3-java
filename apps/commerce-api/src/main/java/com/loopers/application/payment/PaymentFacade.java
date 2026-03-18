package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.client.PgPaymentDto;
import com.loopers.infrastructure.client.PgPaymentGateway;
import com.loopers.infrastructure.client.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentFacade {
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PgPaymentGateway pgPaymentGateway;
    private final String callbackUrl;

    public PaymentFacade(
            PaymentRepository paymentRepository,
            OrderService orderService,
            PgPaymentGateway pgPaymentGateway,
            @Value("${payment.callback-url}") String callbackUrl
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.pgPaymentGateway = pgPaymentGateway;
        this.callbackUrl = callbackUrl;
    }

    @Transactional
    public PaymentInfo requestPayment(Long userId, PaymentCommand command) {
        OrderInfo order = orderService.getOrder(userId, command.orderId());

        if (order.status() != Order.Status.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }

        String pgOrderCode = UUID.randomUUID().toString();
        Payment payment = paymentRepository.save(
                Payment.create(command.orderId(), pgOrderCode, command.cardType(), command.cardNo(), order.finalAmount())
        );

        Optional<PgPaymentDto.TransactionResponse> pgResponse = pgPaymentGateway.requestPayment(
                String.valueOf(userId),
                new PgPaymentDto.PaymentRequest(pgOrderCode, command.cardType().name(), command.cardNo(), order.finalAmount(), callbackUrl)
        );
        pgResponse.ifPresent(r -> payment.assignPgTransaction(r.transactionKey()));

        return PaymentInfo.from(payment);
    }

    @Transactional
    public PaymentInfo syncPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                                           .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제건이 존재하지 않습니다."));

        orderService.getOrder(userId, payment.getOrderId()); // 소유권 검증

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentInfo.from(payment);
        }

        pgPaymentGateway.getTransactionsByOrder(String.valueOf(userId), payment.getPgOrderCode())
                .flatMap(response -> response.transactions().stream()
                        .filter(t -> t.pgStatus().hasResult())
                        .findFirst())
                .ifPresent(t -> {
                    payment.assignPgTransaction(t.transactionKey());
                    applyPgResult(payment, t.pgStatus(), t.reason());
                });

        return PaymentInfo.from(payment);
    }

    @Transactional
    public void handleCallback(PgCallbackCommand command) {
        Payment payment = paymentRepository.findByPgTransactionKey(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제건이 존재하지 않습니다. transactionKey: " + command.transactionKey()));

        PgTransactionStatus pgStatus = PgTransactionStatus.from(command.status());
        if (pgStatus == PgTransactionStatus.UNKNOWN) {
            throw new CoreException(ErrorType.BAD_REQUEST, "처리할 수 없는 PG 상태입니다: " + command.status());
        }

        applyPgResult(payment, pgStatus, command.reason());
    }

    private void applyPgResult(Payment payment, PgTransactionStatus status, String reason) {
        switch (status) {
            case SUCCESS -> {
                payment.complete();
                orderService.markOrderPaid(payment.getOrderId());
            }
            case FAILED -> {
                payment.fail(reason);
                orderService.markOrderFailed(payment.getOrderId());
            }
        }
    }
}

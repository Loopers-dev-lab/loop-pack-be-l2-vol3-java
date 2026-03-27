package com.loopers.application.payment;

import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.observability.annotation.LogBusinessSuccess;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentUseCase {

    private final PaymentStartApplicationService paymentStartApplicationService;
    private final PaymentCancelApplicationService paymentCancelApplicationService;
    private final PaymentCompleteService paymentCompleteService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OrderApplicationService orderApplicationService;

    @LogBusinessSuccess(action = "PAYMENT_START", domain = "payment", memberIdArg = "memberId", aggregateIdArg = "orderId")
    public Payment start(String memberId, UUID orderId, CardType cardType, String cardNo, String callbackUrl) {
        Order order = orderApplicationService.getById(new OrderAccessRequest(orderId, memberId, false));
        if (order.isCancelled()) {
            throw new CoreException(ErrorType.CONFLICT, "취소된 주문은 결제를 시작할 수 없습니다.");
        }

        StartPaymentCommand command = new StartPaymentCommand(
                memberId,
                orderId,
                cardType,
                cardNo,
                order.totalAmount(),
                callbackUrl
        );
        return paymentStartApplicationService.start(command);
    }

    @Transactional
    @LogBusinessSuccess(action = "PAYMENT_CANCEL", domain = "payment", memberIdArg = "command", aggregateIdArg = "command")
    public Payment cancel(CancelPaymentCommand command) {
        return paymentCancelApplicationService.cancel(command);
    }

    @Transactional
    @LogBusinessSuccess(action = "PAYMENT_COMPLETE", domain = "payment", memberIdArg = "command", aggregateIdArg = "command")
    public Payment complete(CompletePaymentCommand command) {
        return paymentCompleteService.complete(command);
    }

    @Transactional
    @LogBusinessSuccess(action = "PAYMENT_RECONCILE", domain = "payment", memberIdArg = "memberId", aggregateIdArg = "orderId")
    public Payment reconcile(String memberId, UUID orderId) {
        Payment payment = paymentRepository.findByMemberIdAndOrderId(memberId, orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
        Order order = orderApplicationService.getById(new OrderAccessRequest(orderId, memberId, false));

        if (payment.status() != PaymentStatus.REQUESTED
                && payment.status() != PaymentStatus.CANCEL_REQUESTED
                && payment.status() != PaymentStatus.CANCEL_RECONCILE_REQUIRED
                && !(order.isCancelled() && payment.status() == PaymentStatus.SUCCEEDED)) {
            return payment;
        }

        Payment target = payment;
        if (target.pgTransactionKey() == null || target.pgTransactionKey().isBlank()) {
            boolean isRetryTargetOnCancelledOrder = order.isCancelled()
                    && (target.status() == PaymentStatus.REQUESTED
                    || target.status() == PaymentStatus.CANCEL_REQUESTED
                    || target.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED);
            int maxLookupAttempts = isRetryTargetOnCancelledOrder ? 3 : 1;

            for (int attempt = 0; attempt < maxLookupAttempts; attempt++) {
                List<PaymentGateway.PaymentGatewayTransaction> transactions;
                try {
                    transactions = paymentGateway.getPaymentsByOrderId(memberId, orderId.toString());
                } catch (CoreException e) {
                    if (e.getErrorType() != ErrorType.NOT_FOUND && e.getErrorType() != ErrorType.INTERNAL_ERROR) {
                        throw e;
                    }
                    if (attempt + 1 >= maxLookupAttempts) {
                        if (order.isCancelled() && target.status() == PaymentStatus.REQUESTED) {
                            Payment reconcileRequired = target
                                    .markCancelReconcileRequiredFromRequested("PG 주문 결제 조회 재처리 대기");
                            return paymentRepository.save(reconcileRequired);
                        }
                        return target;
                    }

                    try {
                        Thread.sleep((attempt + 1L) * 50L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return target;
                    }
                    continue;
                }

                if (transactions.isEmpty()) {
                    if (attempt + 1 >= maxLookupAttempts) {
                        if (order.isCancelled() && target.status() == PaymentStatus.REQUESTED) {
                            Payment reconcileRequired = target
                                    .markCancelReconcileRequiredFromRequested("PG 주문 결제 조회 재처리 대기");
                            return paymentRepository.save(reconcileRequired);
                        }
                        return target;
                    }

                    try {
                        Thread.sleep((attempt + 1L) * 50L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return target;
                    }
                    continue;
                }

                if (transactions.size() != 1) {
                    throw new CoreException(ErrorType.CONFLICT, "PG 주문 결제 내역이 여러 건 존재해 결제를 수렴할 수 없습니다.");
                }

                PaymentGateway.PaymentGatewayTransaction transaction = transactions.get(0);
                target = paymentRepository.save(
                        new Payment(
                                target.id(),
                                target.memberId(),
                                target.orderId(),
                                target.cardType(),
                                target.cardNo(),
                                target.amount(),
                                target.status(),
                                transaction.transactionKey(),
                                target.reason(),
                                target.createdAt(),
                                target.updatedAt(),
                                target.deletedAt()
                        )
                );
                break;
            }

            if (target.pgTransactionKey() == null || target.pgTransactionKey().isBlank()) {
                if (order.isCancelled() && target.status() == PaymentStatus.REQUESTED) {
                    Payment reconcileRequired = target
                            .markCancelReconcileRequiredFromRequested("PG 주문 결제 조회 재처리 대기");
                    return paymentRepository.save(reconcileRequired);
                }
                return target;
            }
        }

        if (order.isCancelled() && target.status() == PaymentStatus.REQUESTED) {
            try {
                return paymentCancelApplicationService.cancel(new CancelPaymentCommand(memberId, orderId));
            } catch (CoreException e) {
                if (e.getErrorType() != ErrorType.NOT_FOUND && e.getErrorType() != ErrorType.INTERNAL_ERROR) {
                    throw e;
                }
            }
        }

        Payment completed;
        try {
            completed = paymentCompleteService.complete(
                    new CompletePaymentCommand(memberId, target.pgTransactionKey())
            );
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND || e.getErrorType() == ErrorType.INTERNAL_ERROR) {
                return target;
            }
            throw e;
        }

        if (order.isCancelled()
                && (completed.status() == PaymentStatus.REQUESTED
                || completed.status() == PaymentStatus.SUCCEEDED
                || completed.status() == PaymentStatus.CANCEL_REQUESTED
                || completed.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED)) {
            try {
                return paymentCancelApplicationService.cancel(new CancelPaymentCommand(memberId, orderId));
            } catch (CoreException e) {
                if (e.getErrorType() == ErrorType.NOT_FOUND || e.getErrorType() == ErrorType.INTERNAL_ERROR) {
                    Payment reconcileRequired = completed.requestCancel()
                            .markCancelReconcileRequired("결제 취소 재처리 대기");
                    return paymentRepository.save(reconcileRequired);
                }
                throw e;
            }
        }

        return completed;
    }
}

package com.loopers.domain.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public PaymentModel createOrGet(Long userId, Long orderId, Long amount, CardType cardType, String cardNo) {
        return paymentRepository.findByOrderId(orderId)
            .map(existing -> {
                if (!existing.getUserId().equals(userId)) {
                    throw new CoreException(ErrorType.CONFLICT, "다른 사용자의 결제 건입니다.");
                }
                return existing;
            })
            .orElseGet(() -> paymentRepository.save(new PaymentModel(userId, orderId, amount, cardType, cardNo)));
    }

    @Transactional(readOnly = true)
    public PaymentModel getMyPayment(Long userId, Long orderId) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "본인 결제만 조회할 수 있습니다.");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findRecoverTargets(int size, int staleSeconds) {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(Math.max(staleSeconds, 1));
        return paymentRepository.findRecoverTargets(threshold, size);
    }

    @Transactional
    public PaymentModel applyExternalResult(Long paymentId, String paymentKey, PaymentStatus status, String reason) {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        switch (status) {
            case PENDING -> {
                if (paymentKey != null && !paymentKey.isBlank()) {
                    payment.markRequestAccepted(paymentKey);
                } else {
                    payment.markPendingWithError(reason);
                }
            }
            case SUCCESS -> {
                payment.markSuccess(paymentKey);
                updateOrderStatus(payment.getOrderId(), true);
            }
            case FAILED_LIMIT_EXCEEDED, FAILED_INVALID_CARD, FAILED -> {
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                    return payment;
                }
                payment.markFailed(status, reason);
                updateOrderStatus(payment.getOrderId(), false);
            }
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "적용할 수 없는 결제 상태입니다.");
        }

        return payment;
    }

    @Transactional
    public PaymentModel markPendingWithError(Long paymentId, String reason) {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        payment.markPendingWithError(reason);
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentModel findByPaymentKey(String paymentKey) {
        return paymentRepository.findByPgPaymentKey(paymentKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    private void updateOrderStatus(Long orderId, boolean paid) {
        OrderModel order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (paid) {
            order.markPaid();
            return;
        }
        order.markPaymentFailed();
    }
}

package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.domain.payment.CardType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Transactional
    public PaymentModel preparePayment(Long userId, PaymentCommand command) {
        OrderModel order = orderService.getOrder(command.orderId(), userId);
        order.startPayment();

        PaymentModel payment = new PaymentModel(
            order.getId(),
            userId,
            command.cardType(),
            command.maskedCardNo(),
            order.finalAmount()
        );
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentModel save(PaymentModel payment) {
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 거래를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> getByOrderId(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> getPendingPayments() {
        return paymentRepository.findAllByStatus(PaymentStatus.PENDING);
    }

    @Transactional
    public void assignTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = getById(paymentId);
        payment.assignTransactionKey(transactionKey);
    }
}

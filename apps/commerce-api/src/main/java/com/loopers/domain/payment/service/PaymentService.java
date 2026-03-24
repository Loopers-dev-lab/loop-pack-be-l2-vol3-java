package com.loopers.domain.payment.service;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentProduct;
import com.loopers.domain.payment.repository.PaymentProductRepository;
import com.loopers.domain.payment.repository.PaymentRepository;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProductRepository paymentProductRepository;
    private final ProductService productService;

    public Payment createPayment(PaymentCommand.Create command) {
        Payment payment = Payment.create(
                command.orderId(), command.memberId(),
                command.cardType(), command.cardNo(), command.amount()
        );
        return paymentRepository.save(payment);
    }

    public Payment createPaymentWithSnapshots(PaymentCommand.Create command, List<OrderProduct> orderProducts) {
        Payment saved = createPayment(command);
        List<PaymentProduct> snapshots = orderProducts.stream()
                .map(op -> PaymentProduct.create(
                        saved.getId(),
                        op.getProductId(),
                        op.getProductName().value(),
                        op.getPrice().value(),
                        op.getQuantity().value()
                ))
                .toList();
        paymentProductRepository.saveAll(snapshots);
        return saved;
    }

    public void markRequested(Long paymentId, String transactionKey) {
        Payment payment = getPaymentById(paymentId);
        payment.markRequested(transactionKey);
        paymentRepository.update(payment);
    }

    public void markSuccess(String transactionKey) {
        Payment payment = getPaymentByTransactionKey(transactionKey);
        payment.markSuccess();
        paymentRepository.update(payment);
    }

    public void markFailed(String transactionKey, String failReason) {
        Payment payment = getPaymentByTransactionKey(transactionKey);
        payment.markFailed(failReason);
        paymentRepository.update(payment);
    }

    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    public Payment getPaymentByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    public void markFailedById(Long paymentId, String failReason) {
        Payment payment = getPaymentById(paymentId);
        payment.markFailed(failReason);
        paymentRepository.update(payment);
    }

    public void handlePgFailure(Long paymentId) {
        markFailedById(paymentId, "PG 서비스 장애");
        restoreStock(paymentId);
    }

    public void restoreStock(Long paymentId) {
        List<PaymentProduct> snapshots = paymentProductRepository.findByPaymentId(paymentId);
        for (PaymentProduct snapshot : snapshots) {
            productService.increaseStockAtomic(snapshot.getProductId(), snapshot.getQuantity());
        }
    }

    private Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }
}

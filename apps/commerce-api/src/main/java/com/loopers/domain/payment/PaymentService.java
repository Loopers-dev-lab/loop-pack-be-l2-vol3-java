package com.loopers.domain.payment;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.order.Order;
import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 결제 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 새로운 결제를 생성한다.
     *
     * @param order         결제 대상 주문
     * @param paymentMethod 결제 수단 정보
     * @return 생성된 결제 (상태: READY)
     */
    @Transactional
    public Payment create(Order order, PaymentMethod paymentMethod) {
        Payment payment = Payment.create(NewPayment.from(order, paymentMethod));
        return paymentRepository.save(payment);
    }

    /**
     * 결제를 확정한다. READY → PENDING 전이 및 transactionKey 할당.
     *
     * @param paymentId      결제 ID
     * @param transactionKey PG 거래 키
     * @return 확정된 결제 (상태: PENDING)
     * @throws CoreException 결제가 존재하지 않거나 READY 상태가 아닌 경우
     */
    @Transactional
    public Payment confirmPayment(Long paymentId, String transactionKey) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        payment.confirmPayment(transactionKey);
        return payment;
    }

    /**
     * 거래 키로 결제를 조회한다.
     *
     * @param transactionKey PG 거래 키
     * @return 결제
     * @throws CoreException 결제가 존재하지 않는 경우
     */
    public Payment getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
    }
}

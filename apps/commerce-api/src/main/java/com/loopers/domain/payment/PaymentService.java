package com.loopers.domain.payment;

import org.springframework.transaction.annotation.Transactional;

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
     * @param newPayment 결제 생성 정보
     * @return 생성된 결제 (상태: PENDING)
     */
    @Transactional
    public Payment create(NewPayment newPayment) {
        Payment payment = Payment.create(newPayment);
        return paymentRepository.save(payment);
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

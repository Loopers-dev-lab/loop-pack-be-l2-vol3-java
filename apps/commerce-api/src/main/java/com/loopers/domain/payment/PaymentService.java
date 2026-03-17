package com.loopers.domain.payment;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;

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
}

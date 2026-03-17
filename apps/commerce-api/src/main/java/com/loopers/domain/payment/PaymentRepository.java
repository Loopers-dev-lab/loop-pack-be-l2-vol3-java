package com.loopers.domain.payment;

/**
 * 결제 도메인 리포지토리 인터페이스.
 */
public interface PaymentRepository {

    /**
     * 결제를 저장한다.
     *
     * @param payment 저장할 결제
     * @return 저장된 결제
     */
    Payment save(Payment payment);
}

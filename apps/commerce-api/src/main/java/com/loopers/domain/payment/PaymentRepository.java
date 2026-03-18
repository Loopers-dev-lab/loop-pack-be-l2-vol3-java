package com.loopers.domain.payment;

import java.util.Optional;

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

    /**
     * ID로 결제를 조회한다.
     *
     * @param id 결제 ID
     * @return 결제 (존재하지 않으면 빈 Optional)
     */
    Optional<Payment> findById(Long id);

    /**
     * 거래 키로 결제를 조회한다.
     *
     * @param transactionKey PG 거래 키
     * @return 결제 (존재하지 않으면 빈 Optional)
     */
    Optional<Payment> findByTransactionKey(String transactionKey);
}

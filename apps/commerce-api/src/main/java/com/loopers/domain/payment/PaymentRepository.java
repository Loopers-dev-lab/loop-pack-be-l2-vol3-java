package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
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

    List<Payment> findPendingPaymentsBefore(ZonedDateTime threshold);

    /**
     * 기준 시각 이전에 READY 상태로 남아 있는 결제 목록을 조회한다.
     *
     * @param threshold 기준 시각
     * @return READY 상태이며 updatedAt이 기준 시각 이전인 결제 목록
     */
    List<Payment> findReadyPaymentsBefore(ZonedDateTime threshold);
}

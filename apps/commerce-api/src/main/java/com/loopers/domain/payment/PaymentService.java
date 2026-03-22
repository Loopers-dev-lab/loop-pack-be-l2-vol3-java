package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;

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

    /**
     * 기준 시각 이전에 PENDING 상태로 남아 있는 결제 목록을 조회한다.
     *
     * <p>PG 승인 요청 후 응답을 받지 못한 채
     * PENDING 상태에 머물러 있는 결제를 복구하기 위해 사용된다.</p>
     *
     * @param threshold 기준 시각
     * @return PENDING 상태이며 updatedAt이 기준 시각 이전인 결제 목록
     */
    public List<Payment> getPendingPaymentsBefore(ZonedDateTime threshold) {
        return paymentRepository.findPendingPaymentsBefore(threshold);
    }

    /**
     * 기준 시각 이전에 READY 상태로 남아 있는 결제 목록을 조회한다.
     *
     * <p>PG 요청 타임아웃 등으로 transactionKey가 할당되지 않은 채
     * READY 상태에 머물러 있는 결제를 복구하기 위해 사용된다.</p>
     *
     * @param threshold 기준 시각
     * @return READY 상태이며 updatedAt이 기준 시각 이전인 결제 목록
     */
    public List<Payment> getReadyPaymentsBefore(ZonedDateTime threshold) {
        return paymentRepository.findReadyPaymentsBefore(threshold);
    }

    /**
     * 결제를 실패 처리한다.
     *
     * @param paymentId 결제 ID
     * @param reason    실패 사유
     * @return 실패 처리된 결제
     * @throws CoreException 결제가 존재하지 않거나 이미 처리된 경우
     */
    @Transactional
    public Payment fail(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        payment.update(PaymentStatus.FAILED, reason);
        return payment;
    }
}

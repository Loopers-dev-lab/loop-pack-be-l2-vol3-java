package com.loopers.domain.payment;

import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 도메인 서비스.
 * <p>
 * 결제의 순수 도메인 로직(생성, 상태 전이, 조회)을 담당한다.
 * PG 통신과 주문 상태 연동 등 외부 도메인 조합(orchestration)은
 * Facade에서 수행한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제 엔티티를 생성한다. status=REQUESTED로 초기화된다.
     *
     * @param orderId  주문 ID
     * @param userId   사용자 ID
     * @param cardType 카드 종류
     * @param cardNo   카드 번호
     * @param amount   결제 금액
     * @return 저장된 PaymentModel
     */
    @Transactional
    public PaymentModel createPayment(Long orderId, Long userId,
                                       CardType cardType, String cardNo,
                                       BigDecimal amount) {
        return paymentRepository.save(
                PaymentModel.create(orderId, userId, cardType, cardNo, amount)
        );
    }

    /**
     * 결제 상태를 완료(SUCCESS/FAILED)로 전이한다.
     * <p>
     * CAS(Compare-And-Set) 방식으로 REQUESTED → toStatus 전이를 수행한다.
     * CAS 실패 시 false를 반환하여 멱등 처리한다.
     * </p>
     *
     * @param paymentId 결제 ID
     * @param toStatus  전이할 상태 (SUCCESS / FAILED)
     * @param reason    실패 사유 (성공 시 null)
     * @return 상태 전이 성공 여부 (이미 처리된 경우 false)
     */
    @Transactional
    public boolean completePayment(Long paymentId, PaymentStatus toStatus, String reason) {
        int affected = paymentRepository.casUpdateStatus(paymentId, PaymentStatus.REQUESTED, toStatus, reason);
        return affected > 0;
    }

    public Optional<PaymentModel> findById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey);
    }

    /**
     * 해당 주문에 진행 중(REQUESTED)인 결제가 있는지 확인한다.
     *
     * @param orderId 주문 ID
     * @return 진행 중인 결제 존재 여부
     */
    /**
     * PG에서 반환한 transactionKey를 결제 엔티티에 저장한다.
     * <p>
     * TX 분리 패턴에서 TX-2 역할: PG 호출 후 별도 트랜잭션으로 transactionKey를 저장한다.
     * </p>
     *
     * @param paymentId      결제 ID
     * @param transactionKey PG 트랜잭션 식별자
     * @return 업데이트된 PaymentModel
     */
    @Transactional
    public PaymentModel assignTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        payment.assignTransactionKey(transactionKey);
        return payment;
    }

    public boolean hasActivePayment(Long orderId) {
        return paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED);
    }

    /**
     * 지정 시간 이전에 생성된 REQUESTED 상태 결제 목록을 조회한다.
     *
     * @param before 기준 시간
     * @return REQUESTED 상태 결제 목록
     */
    public List<PaymentModel> findRequestedBefore(LocalDateTime before) {
        return paymentRepository.findAllRequestedBefore(before);
    }
}

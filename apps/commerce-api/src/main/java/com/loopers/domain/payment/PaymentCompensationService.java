package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 보정 도메인 서비스.
 * <p>
 * stock commit 실패 등 후속 처리 실패 시 보정 테이블에 기록한다.
 * {@code REQUIRES_NEW}로 별도 트랜잭션을 사용하여 외부 TX 롤백에 영향받지 않는다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final PaymentCompensationRepository compensationRepository;

    /**
     * stock commit 실패 등 후속 처리 실패를 보정 테이블에 기록한다.
     * <p>
     * {@code REQUIRES_NEW}: 호출자의 TX가 롤백되어도 보정 기록은 유지된다.
     * 동일 paymentId에 대한 중복 기록은 멱등 처리(무시)한다.
     * </p>
     *
     * @param paymentId 결제 ID
     * @param orderId   주문 ID
     * @param reason    실패 사유
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedCommit(Long paymentId, Long orderId, String reason) {
        if (compensationRepository.existsByPaymentId(paymentId)) {
            log.info("보정 기록 이미 존재 — 멱등 처리. paymentId={}", paymentId);
            return;
        }
        compensationRepository.save(
                PaymentCompensationModel.create(paymentId, orderId, reason)
        );
        log.warn("보정 기록 생성. paymentId={}, orderId={}, reason={}", paymentId, orderId, reason);
    }
}

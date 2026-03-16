package com.loopers.domain.payment;

import com.loopers.domain.payment.vo.RefOrderId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel createPending(Long refOrderId, Long refMemberId, CardType cardType, String cardNo, BigDecimal amount) {
        PaymentModel payment = PaymentModel.create(refOrderId, refMemberId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentModel updateRequested(Long paymentId, String pgTransactionKey) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. id=" + paymentId));
        payment.requested(pgTransactionKey);
        return payment;
    }

    @Transactional
    public PaymentModel updateCompleted(String pgTransactionKey, BigDecimal pgAmount) {
        PaymentModel payment = paymentRepository.findByPgTransactionId(pgTransactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. pgTransactionKey=" + pgTransactionKey));
        if (payment.getAmount().compareTo(pgAmount) != 0) {
            log.warn("이상 거래 감지: 금액 불일치. pgTransactionKey={}, 저장={}, PG={}", pgTransactionKey, payment.getAmount(), pgAmount);
            payment.fail();
            return payment;
        }
        payment.complete();
        return payment;
    }

    @Transactional
    public PaymentModel updateFailed(String pgTransactionKey) {
        PaymentModel payment = paymentRepository.findByPgTransactionId(pgTransactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. pgTransactionKey=" + pgTransactionKey));
        payment.fail();
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. id=" + paymentId));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByPgTransactionKey(String pgTransactionKey) {
        return paymentRepository.findByPgTransactionId(pgTransactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. pgTransactionKey=" + pgTransactionKey));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByRefOrderId(Long orderId) {
        return paymentRepository.findByRefOrderId(new RefOrderId(orderId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다. orderId=" + orderId));
    }
}

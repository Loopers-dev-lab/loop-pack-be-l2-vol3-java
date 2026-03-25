package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // 결제 생성
    @Transactional
    public Payment create(Long orderId, Long userId, int amount, String cardType, String cardNo) {
        Payment payment = new Payment(orderId, userId, amount, cardType, cardNo);
        return paymentRepository.save(payment);
    }

    // 해당 주문에 대한 결제가 이미 존재하는지 확인
    @Transactional(readOnly = true)
    public boolean existsByOrderId(Long orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }

    // 결제 단건 조회
    @Transactional(readOnly = true)
    public Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
    }

    // 주문 ID로 결제 조회 (결제 상태 확인용)
    @Transactional(readOnly = true)
    public Payment findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 주문의 결제 정보가 없습니다."));
    }

    // PG transactionKey로 결제 조회 (콜백 수신용)
    @Transactional(readOnly = true)
    public Payment findByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 거래 ID의 결제 정보가 없습니다."));
    }

    // 폴링 스케줄러용: REQUESTED 상태이면서 일정 시간 이전에 생성된 결제 목록
    @Transactional(readOnly = true)
    public List<Payment> findAllRequested(ZonedDateTime before) {
        return paymentRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PENDING, before);
    }

    // 조건부 상태 전이: PENDING인 경우에만 UPDATE, true=처리됨 false=이미 최종 상태
    @Transactional
    public boolean markSuccessIfPending(Long id, String transactionKey) {
        return paymentRepository.markSuccessIfPending(id, transactionKey);
    }

    @Transactional
    public boolean markFailedIfPending(Long id, String transactionKey, String failureReason) {
        return paymentRepository.markFailedIfPending(id, transactionKey, failureReason);
    }

    @Transactional
    public boolean markTimeoutIfPending(Long id) {
        return paymentRepository.markTimeoutIfPending(id);
    }
}

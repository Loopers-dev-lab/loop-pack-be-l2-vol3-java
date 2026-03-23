package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PasswordEncryptor passwordEncryptor;
    private final PaymentTxService paymentTxService;

    /**
     * 결제 요청 처리 흐름:
     * 1. 사용자 인증
     * 2. 주문 검증 (PENDING 상태, 소유자 확인)
     * 3. 중복 결제 확인
     * 4. [TX] Payment(PENDING) 생성 및 저장
     * 5. PG API 호출 (트랜잭션 외부)
     *    - 성공 시: pgTransactionId 저장 → 콜백 대기
     *    - 실패 시: PG 상태 조회로 복구 시도 → 불가 시 PENDING 유지
     */
    public PaymentDto.PaymentInfo pay(String loginId, String rawPassword, PaymentDto.PayCommand command) {
        User user = authenticate(loginId, rawPassword);

        Order order = orderRepository.findByIdAndUserId(command.orderId(), user.getId())
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.ORDER_INVALID_STATUS_TRANSITION);
        }

        Optional<Payment> existingPayment = paymentRepository.findByOrderId(command.orderId());
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                throw new CoreException(ErrorType.PAYMENT_ALREADY_PAID);
            }
            if (existing.getStatus() == PaymentStatus.PENDING) {
                // 멱등: 이미 진행 중인 결제 반환
                return PaymentDto.PaymentInfo.from(existing);
            }
            // FAILED: 재시도 허용
        }

        // [TX] Payment(PENDING) 생성
        Payment payment = paymentTxService.createPayment(
            command.orderId(), user.getId(), command.cardType(), command.cardNo(), order.getTotalAmount()
        );

        // PG API 호출 (TX 외부)
        Optional<String> transactionId = pgClient.requestPayment(
            user.getId(), command.orderId(), command.cardType(), command.cardNo(), order.getTotalAmount()
        );

        if (transactionId.isPresent()) {
            // PG가 요청을 접수함 → txId 저장, 콜백으로 최종 결과 수신 대기
            paymentTxService.assignTransactionId(payment.getId(), transactionId.get());
        } else {
            // PG 호출 실패(타임아웃/서킷 오픈) → 상태 조회로 복구 시도
            pgClient.getPaymentByOrderId(user.getId(), command.orderId())
                .ifPresent(info -> paymentTxService.processPaymentResult(
                    payment.getId(), command.orderId(), info.transactionKey(), info.status()
                ));
        }

        return PaymentDto.PaymentInfo.from(
            paymentRepository.findByOrderId(command.orderId()).orElse(payment)
        );
    }

    /**
     * PG 콜백 처리:
     * - 이미 처리된 결제는 멱등하게 무시
     * - SUCCESS → Order CONFIRMED
     * - FAILED  → Order CANCELLED + 재고/쿠폰 복구
     */
    public PaymentDto.PaymentInfo handleCallback(PaymentDto.CallbackCommand command) {
        Long orderId = Long.parseLong(command.orderId());

        Payment payment = paymentRepository.findByPgTransactionId(command.transactionId())
            .or(() -> paymentRepository.findByOrderId(orderId))
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            // 멱등: 이미 처리됨
            return PaymentDto.PaymentInfo.from(payment);
        }

        paymentTxService.processPaymentResult(
            payment.getId(), payment.getOrderId(), command.transactionId(), command.status()
        );

        return PaymentDto.PaymentInfo.from(
            paymentRepository.findByOrderId(payment.getOrderId()).orElse(payment)
        );
    }

    /**
     * 수동 상태 동기화 (관리자):
     * 콜백 미수신 등으로 PENDING에 머문 결제건을 PG 조회 후 수동 반영
     */
    @Transactional(readOnly = true)
    public PaymentDto.PaymentInfo getPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        return PaymentDto.PaymentInfo.from(payment);
    }

    public PaymentDto.PaymentInfo syncPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentDto.PaymentInfo.from(payment);
        }

        pgClient.getPaymentByOrderId(payment.getUserId(), orderId)
            .ifPresent(info -> paymentTxService.processPaymentResult(
                payment.getId(), orderId, info.transactionKey(), info.status()
            ));

        return PaymentDto.PaymentInfo.from(
            paymentRepository.findByOrderId(orderId).orElse(payment)
        );
    }

    private User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        if (!passwordEncryptor.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }
        return user;
    }
}

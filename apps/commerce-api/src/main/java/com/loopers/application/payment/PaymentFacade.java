package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentEventPublisher;
import com.loopers.domain.payment.PaymentRequestEvent;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentEventPublisher eventPublisher;

    /**
     * 결제 요청 (Transaction 1)
     *
     * ① 주문 조회 + 소유권 확인 + 상태 확인 (PENDING_PAYMENT만 결제 가능)
     * ② Payment 생성 (status=PENDING)
     * ③ PaymentRequestEvent 발행
     * → COMMIT 후 PaymentEventListener가 PG API 호출
     */
    @Transactional
    public PaymentInfo requestPayment(Long userId, PaymentCommand command) {
        // ① 주문 검증
        Order order = orderService.findById(command.orderId());
        // 요청한 유저와 주문의 유저가 동일한지 확인
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        // 결제상태 확인
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문만 결제할 수 있습니다.");
        }

        // 중복 결제 방어 (앱 레벨 — DB UNIQUE 이전에 명시적 검증)
        if (paymentService.existsByOrderId(command.orderId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중인 주문입니다.");
        }

        // ② Payment 생성
        Payment payment = paymentService.create(
                order.getId(),
                userId,
                order.getFinalAmount().getAmount(),
                command.cardType(),
                command.cardNo()
        );

        // ③ 이벤트 발행 (AFTER_COMMIT에서 PG 호출)
        eventPublisher.publish(new PaymentRequestEvent(payment.getId(), order.getId(), userId));

        return PaymentInfo.from(payment);
    }

    // 결제 상태 조회 (주문 ID 기준, 프론트엔드 폴링용)
    @Transactional(readOnly = true)
    public PaymentInfo findByOrderId(Long orderId, Long userId) {
        Order order = orderService.findById(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }

        Payment payment = paymentService.findByOrderId(orderId);
        return PaymentInfo.from(payment);
    }
}

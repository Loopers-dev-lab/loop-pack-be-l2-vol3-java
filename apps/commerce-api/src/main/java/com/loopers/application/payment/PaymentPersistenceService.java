package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 결제 PENDING 저장만 담당. 트랜잭션 경계 분리를 위해 Facade와 분리 (06 §5.1).
 * PaymentFacade는 이 서비스 호출 후(커밋 후) PG를 호출한다.
 */
@Service
public class PaymentPersistenceService {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    public PaymentPersistenceService(OrderService orderService, PaymentRepository paymentRepository) {
        this.orderService = orderService;
        this.paymentRepository = paymentRepository;
    }

    /**
     * 주문 검증 후 PENDING 결제를 저장한다. 단일 트랜잭션으로 커밋된다.
     * 호출 후 트랜잭션 밖에서 PG를 호출해야 한다.
     */
    @Transactional
    public PendingPaymentResult savePendingAndGetRequestParam(Long userId, Long orderId,
                                                              String cardType, String cardNo,
                                                              String callbackUrl) {
        OrderModel order = orderService.findById(userId, orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제할 수 없는 주문 상태입니다. ORDERED 상태에서만 결제 가능합니다.");
        }
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제 진행 중인 주문입니다.");
        }
        PaymentModel payment = PaymentModel.createPending(orderId);
        payment = paymentRepository.save(payment);
        BigDecimal finalAmount = order.getFinalAmount();
        PaymentRequestParam param = PaymentRequestParam.of(orderId, cardType, cardNo, finalAmount, callbackUrl);
        PaymentInfo info = PaymentInfo.from(payment);
        return new PendingPaymentResult(info, param);
    }
}

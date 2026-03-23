package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;

    @Value("${app.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private String callbackUrl;

    /**
     * 결제 요청
     * 1. 주문 조회
     * 2. Payment 생성 (PENDING)
     * 3. PG 호출 (트랜잭션 밖)
     * 4. PG 응답 반환 (비동기 처리이므로 PENDING 상태로 반환)
     */
    public PaymentInfo requestPayment(PaymentCommand command) {
        // 1. 주문 조회 및 검증
        Order order = getOrder(command.orderId());

        // 2. Payment 생성
        Payment payment = paymentService.createPayment(order.getId());

        // 3. PG 호출 (트랜잭션 밖에서 실행)
        PgPaymentRequest pgRequest = PgPaymentRequest.of(
            String.valueOf(command.userId()),
            order.getId(),
            command.cardType(),
            command.cardNo(),
            order.getTotalPrice().value().longValue(),
            callbackUrl
        );

        PgPaymentResponse pgResponse = pgClient.requestPayment(pgRequest);
        log.info("PG 결제 요청 완료. transactionKey={}, status={}",
            pgResponse.transactionKey(), pgResponse.status());

        return PaymentInfo.from(payment, order);
    }

    /**
     * PG 콜백 수신
     * 1. Payment 조회
     * 2. 결과에 따라 Payment 상태 업데이트
     * 3. 성공 시 Order 상태도 CONFIRMED로 변경
     */
    @Transactional
    public PaymentInfo handleCallback(PaymentCallbackCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Payment.PAYMENT_NOT_FOUND));

        Order order = getOrder(command.orderId());

        if (!payment.isPending()) {
            log.info("이미 처리된 결제 콜백 수신 (멱등 처리). orderId={}, status={}", command.orderId(), payment.getStatus());
            return PaymentInfo.from(payment, order);
        }

        if (command.isSuccess()) {
            payment.markSuccess(command.transactionKey());
            paymentRepository.save(payment);

            order.confirm();
            orderRepository.save(order);

            log.info("결제 성공. orderId={}, transactionKey={}", command.orderId(), command.transactionKey());
        } else {
            payment.markFailed(command.reason());
            paymentRepository.save(payment);

            log.info("결제 실패. orderId={}, reason={}", command.orderId(), command.reason());
        }

        return PaymentInfo.from(payment, order);
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Order.ORDER_NOT_FOUND));
    }
}

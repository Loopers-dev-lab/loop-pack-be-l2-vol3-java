package com.loopers.application.payment;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.NewPayment;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.TransactionResult;

import lombok.RequiredArgsConstructor;

/**
 * 주문에 대한 결제를 생성한다.
 *
 * <p>주문 조회 및 소유권 검증, PG 결제 요청, 결제 엔티티 저장을 하나의 트랜잭션으로 처리한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class CreatePaymentUseCase {

    private final OrderService orderService;
    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    /**
     * @param command 결제 생성 커맨드
     * @return 생성된 결제 정보
     */
    @Transactional
    public CreatePaymentResult execute(CreatePaymentCommand command) {
        Order order = orderService.getMyOrder(command.userId(), command.orderKey());
        order.validatePayable();

        PaymentRequest paymentRequest = new PaymentRequest(
                order.getOrderKey(),
                command.cardType(),
                command.cardNo(),
                order.getTotalPrice().getAmount(),
                command.callbackUrl()
        );
        TransactionResult transactionResult = paymentGateway.requestPayment(command.userId(), paymentRequest);

        NewPayment newPayment = command.toNewPayment(
                order.getId(),
                transactionResult.transactionKey(),
                order.getTotalPrice()
        );
        Payment payment = paymentService.create(newPayment);

        return CreatePaymentResult.from(payment);
    }
}

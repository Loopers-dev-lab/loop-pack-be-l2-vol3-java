package com.loopers.application.payment;

import com.loopers.application.payment.dto.CreatePaymentReqDto;
import com.loopers.application.payment.dto.FindPaymentResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.event.PaymentRequestedEvent;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentStatus;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final MemberService memberService;
    private final OrderService orderService;
    private final OrderProductService orderProductService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${payment.callback-url}")
    private String callbackUrl;

    @Transactional(rollbackFor = Exception.class)
    public FindPaymentResDto createPayment(String loginId, String password, CreatePaymentReqDto dto) {
        Member member = memberService.findMember(loginId, password);
        Orders order = orderService.getOrderByOrderNumber(dto.orderId());
        if (!order.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        String amount = String.valueOf(order.getTotalPrice().value());
        List<OrderProduct> orderProducts = orderProductService.findByOrderId(order.getId());

        PaymentCommand.Create createCommand = new PaymentCommand.Create(
                order.getOrderNumber(), member.getId(), dto.cardType(), dto.cardNo(), amount
        );
        Payment payment = paymentService.createPaymentWithSnapshots(createCommand, orderProducts);

        eventPublisher.publishEvent(new PaymentRequestedEvent(
                payment.getId(), member.getId(), order.getId(),
                order.getOrderNumber(), dto.cardType(), dto.cardNo(), amount, callbackUrl));

        return FindPaymentResDto.from(payment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String transactionKey, PgPaymentStatus status) {
        Payment payment = paymentService.getPaymentByTransactionKey(transactionKey);
        Orders order = orderService.getOrderByOrderNumber(payment.getOrderId());

        if (status == PgPaymentStatus.SUCCESS) {
            paymentService.markSuccess(transactionKey);
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAID);
            eventPublisher.publishEvent(new PaymentCompletedEvent(payment.getOrderId(), payment.getId()));
        } else {
            paymentService.markFailed(transactionKey, status.name());
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED);
            paymentService.restoreStock(payment.getId());
            eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId(), payment.getId(), status.name()));
        }
    }

    public FindPaymentResDto checkPaymentStatus(String loginId, String password, String orderId) {
        Member member = memberService.findMember(loginId, password);

        Payment payment = paymentService.getPaymentByOrderId(orderId);
        if (!payment.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
        }

        if (payment.getStatus() == PaymentStatus.REQUESTED && payment.getTransactionKey() != null) {
            PaymentInfo info = paymentGateway.getPaymentByOrderId(payment.getMemberId(), orderId);
            if (info != null && info.transactionKey() != null && info.status() != null) {
                if (info.status() != PgPaymentStatus.PENDING) {
                    handleCallback(payment.getTransactionKey(), info.status());
                }
            }
        }

        return FindPaymentResDto.from(payment);
    }
}

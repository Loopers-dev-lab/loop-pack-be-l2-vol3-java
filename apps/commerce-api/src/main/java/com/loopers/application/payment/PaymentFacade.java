package com.loopers.application.payment;

import com.loopers.application.payment.dto.CreatePaymentReqDto;
import com.loopers.application.payment.dto.FindPaymentResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentProduct;
import com.loopers.domain.payment.repository.PaymentProductRepository;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final MemberService memberService;
    private final OrderService orderService;
    private final OrderProductService orderProductService;
    private final PaymentProductRepository paymentProductRepository;
    private final ProductService productService;
    private final TransactionTemplate transactionTemplate;

    @Value("${payment.callback-url}")
    private String callbackUrl;

    public FindPaymentResDto createPayment(String loginId, String password, CreatePaymentReqDto dto) {
        Member member = memberService.findMember(loginId, password);
        Orders order = orderService.getOrderByOrderNumber(dto.orderId());
        String amount = String.valueOf(order.getTotalPrice().value());
        List<OrderProduct> orderProducts = orderProductService.findByOrderId(order.getId());

        // Phase 1: Payment PENDING 생성 + 스냅샷 저장 → 커밋 후 DB 커넥션 반납
        PaymentCommand.Create createCommand = new PaymentCommand.Create(
                order.getOrderNumber(), member.getId(), dto.cardType(), dto.cardNo(), amount
        );
        Payment payment = transactionTemplate.execute(status -> {
            Payment p = paymentService.createPayment(createCommand);
            List<PaymentProduct> snapshots = orderProducts.stream()
                    .map(op -> PaymentProduct.create(
                            p.getId(),
                            op.getProductId(),
                            op.getProductName().value(),
                            op.getPrice().value(),
                            op.getQuantity().value()
                    ))
                    .toList();
            paymentProductRepository.saveAll(snapshots);
            return p;
        });

        // Phase 2: PG 호출 (트랜잭션 없음 — DB 커넥션 미점유)
        PaymentCommand.PgRequest pgCommand = new PaymentCommand.PgRequest(
                dto.orderId(), dto.cardType(), dto.cardNo(), amount, callbackUrl
        );
        PaymentInfo info = paymentGateway.requestPayment(member.getId(), pgCommand);

        if (!info.hasTransactionKey()) {
            // PG 실패 — FAILED 처리 + 재고 복원
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailedById(payment.getId(), "PG 서비스 장애");
                orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED);
                restoreStock(payment.getId());
            });
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 서비스에 일시적인 문제가 발생했습니다.");
        }

        // Phase 3: REQUESTED + 주문 상태 변경
        transactionTemplate.executeWithoutResult(status -> {
            paymentService.markRequested(payment.getId(), info.transactionKey());
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_REQUESTED);
        });

        payment.markRequested(info.transactionKey());
        return FindPaymentResDto.from(payment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String transactionKey, String status) {
        Payment payment = paymentService.getPaymentByTransactionKey(transactionKey);

        Orders order = orderService.getOrderByOrderNumber(payment.getOrderId());

        if ("SUCCESS".equals(status)) {
            paymentService.markSuccess(transactionKey);
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAID);
        } else {
            paymentService.markFailed(transactionKey, status);
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED);
            restoreStock(payment.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public FindPaymentResDto checkPaymentStatus(String loginId, String password, String orderId) {
        memberService.findMember(loginId, password);

        Payment payment = paymentService.getPaymentByOrderId(orderId);

        if (payment.getStatus() == PaymentStatus.REQUESTED && payment.getTransactionKey() != null) {
            PaymentInfo info = paymentGateway.getPaymentByOrderId(payment.getMemberId(), orderId);
            if (info != null && info.transactionKey() != null && info.status() != null) {
                Orders order = orderService.getOrderByOrderNumber(orderId);
                if ("SUCCESS".equals(info.status())) {
                    paymentService.markSuccess(payment.getTransactionKey());
                    orderService.updateOrderStatus(order.getId(), OrderStatus.PAID);
                    payment.markSuccess();
                } else if (!"PENDING".equals(info.status())) {
                    paymentService.markFailed(payment.getTransactionKey(), info.status());
                    orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED);
                    restoreStock(payment.getId());
                    payment.markFailed(info.status());
                }
            }
        }

        return FindPaymentResDto.from(payment);
    }

    private void restoreStock(Long paymentId) {
        List<PaymentProduct> snapshots = paymentProductRepository.findByPaymentId(paymentId);
        for (PaymentProduct snapshot : snapshots) {
            productService.increaseStockAtomic(snapshot.getProductId(), snapshot.getQuantity());
        }
    }
}

package com.loopers.application.payment;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.order.OrderService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PaymentCancelCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmResult;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final StockService stockService;
    private final IssuedCouponService issuedCouponService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final TransactionTemplate transactionTemplate;

    // Command

    @Bulkhead(name = "pg-payment", fallbackMethod = "paymentBulkheadFallback")
    public PaymentInfo requestPayment(Long userId, PaymentCommand.Request command) {
        // TX1: 주문 검증 + Payment 생성 + 비즈니스 확정 (재고 확정 + 주문 PAID)
        Payment payment = transactionTemplate.execute(status -> {
            Order order = validateAndGetOrder(userId, command.orderId());

            PaymentCommand.Create createCommand = PaymentCommand.Create.of(
                    command.orderId(), userId, command.pgType(),
                    command.cardType(), command.cardNo(), order.getFinalAmount());
            Payment created = paymentService.createPayment(createCommand);

            // 비즈니스 먼저 확정
            Map<Long, Integer> productQuantities = extractProductQuantities(order);
            stockService.confirm(productQuantities);
            orderService.payOrder(order.getId());

            return created;
        });

        // PG 라우팅
        PaymentGateway gateway = gatewayRegistry.getGateway(command.pgType());

        // PG 호출 (트랜잭션 밖)
        confirmPaymentWithGateway(payment, gateway);

        // 최종 상태 조회
        Payment updatedPayment = paymentService.getPayment(payment.getId());
        return PaymentInfo.from(updatedPayment);
    }

    @Transactional
    public void handleCallback(String paymentKey, String pgStatus, String reason) {
        Payment payment = paymentService.getPaymentByPaymentKey(paymentKey)
                .orElse(null);
        if (payment == null || payment.isFinalized()) {
            return;
        }

        if ("SUCCESS".equals(pgStatus)) {
            payment.markSucceeded();
        } else {
            payment.markFailed(reason);
            compensate(payment.getOrderId());
        }
    }

    public void cancelPayment(Long userId, Long orderId) {
        Payment payment = paymentService.getLatestPaymentByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));

        doCancelPayment(userId, payment, "주문 취소");
    }

    public PaymentInfo cancelPaymentById(Long userId, Long paymentId, PaymentCommand.Cancel command) {
        Payment payment = paymentService.getPayment(paymentId);

        doCancelPayment(userId, payment, command.cancelReason());

        return PaymentInfo.from(paymentService.getPayment(paymentId));
    }

    private void doCancelPayment(Long userId, Payment payment, String cancelReason) {
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 결제 상태입니다");
        }

        // PG 취소 (트랜잭션 밖)
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        try {
            gateway.cancel(
                    payment.getPaymentKey(),
                    new PaymentCancelCommand(String.valueOf(payment.getOrderId()), cancelReason, payment.getAmount().longValue()));
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 취소에 실패했습니다. 잠시 후 다시 시도해주세요");
        }

        // TX: 결제 취소 + 재고 복원 + 쿠폰 복원 + 주문 취소
        transactionTemplate.executeWithoutResult(status -> {
            paymentService.markCanceled(payment.getId(), cancelReason);

            Order order = orderService.getOrder(payment.getOrderId());
            Map<Long, Integer> productQuantities = extractProductQuantities(order);
            stockService.releaseConfirmed(productQuantities);

            if (order.getIssuedCouponId() != null) {
                issuedCouponService.restore(order.getIssuedCouponId());
            }
            orderService.cancelOrder(order.getId());
        });
    }

    @Bulkhead(name = "pg-payment", fallbackMethod = "verifyBulkheadFallback")
    public PaymentInfo verifyPayment(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        if (payment.isFinalized()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 확정된 결제입니다");
        }

        if (payment.getStatus() == PaymentStatus.REQUESTED) {
            // REQUESTED (transactionKey 없음) → PG 조회 없이 FAILED + 보상
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailed(payment.getId(), "결제 미완료");
                compensate(payment.getOrderId());
            });
            return PaymentInfo.from(paymentService.getPayment(payment.getId()));
        }

        // IN_PROGRESS: PG에서 상태 조회
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        PaymentQueryResult result;
        try {
            result = gateway.query(payment.getPaymentKey());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
        }

        if (result.found() && result.done()) {
            paymentService.markSucceeded(payment.getId());
        } else if (result.found() && !result.done()) {
            // PG에서 아직 처리 중 → 상태 변경 없음
        } else {
            // PG에 결제 정보 없음 또는 실패
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailed(payment.getId(), "결제 미완료");
                compensate(payment.getOrderId());
            });
        }

        return PaymentInfo.from(paymentService.getPayment(payment.getId()));
    }

    // Query

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentDetail(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        return paymentService.getLatestPaymentByOrderId(orderId)
                .map(PaymentInfo::from)
                .orElse(PaymentInfo.empty(orderId));
    }

    public List<PgType> getAvailableMethods() {
        return gatewayRegistry.getAvailableTypes();
    }

    private PaymentInfo paymentBulkheadFallback(Long userId, PaymentCommand.Request command, Throwable t) {
        if (t instanceof CoreException) {
            throw (CoreException) t;
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청이 많습니다. 잠시 후 다시 시도해주세요");
    }

    private PaymentInfo verifyBulkheadFallback(Long userId, Long paymentId, Throwable t) {
        if (t instanceof CoreException) {
            throw (CoreException) t;
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 확인 요청이 많습니다. 잠시 후 다시 시도해주세요");
    }

    private Order validateAndGetOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다");
        }
        if (paymentService.existsActivePayment(orderId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다");
        }
        return order;
    }

    private void confirmPaymentWithGateway(Payment payment, PaymentGateway gateway) {
        PaymentConfirmCommand confirmCommand = new PaymentConfirmCommand(
                payment.getPaymentKey(),
                String.valueOf(payment.getOrderId()),
                payment.getAmount().longValue()
        );

        try {
            PaymentConfirmResult result = gateway.confirm(confirmCommand);
            if (result.success()) {
                // TX: PG 접수 성공 → IN_PROGRESS + 결제 성공
                transactionTemplate.executeWithoutResult(status -> {
                    Payment p = paymentService.getPayment(payment.getId());
                    if (p.isFinalized()) return;
                    p.markSucceeded();
                });
            } else {
                // PG 명시적 실패 → 보상
                transactionTemplate.executeWithoutResult(status -> {
                    paymentService.markFailed(payment.getId(), result.message());
                    compensate(payment.getOrderId());
                });
                throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
            }
        } catch (ResourceAccessException e) {
            log.warn("PG 결제 승인 타임아웃: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            // REQUESTED 상태 유지, 비즈니스 확정 유지 (콜백/verify로 최종 결정)
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("PG 결제 승인 실패: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailed(payment.getId(), e.getMessage());
                compensate(payment.getOrderId());
            });
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
        }
    }

    private void compensate(Long orderId) {
        Order order = orderService.getOrder(orderId);
        Map<Long, Integer> productQuantities = extractProductQuantities(order);
        stockService.releaseConfirmed(productQuantities);

        if (order.getIssuedCouponId() != null) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
        orderService.cancelOrder(orderId);
    }

    private Map<Long, Integer> extractProductQuantities(Order order) {
        return order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
    }
}

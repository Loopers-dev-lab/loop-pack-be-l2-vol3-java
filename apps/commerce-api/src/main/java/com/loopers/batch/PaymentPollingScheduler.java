package com.loopers.batch;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 폴링 스케줄러.
 * 60초 간격으로 REQUESTED 상태가 1분 이상 지속된 결제를 PG에 직접 조회하여 상태를 반영한다.
 * <p>
 * PG 시뮬레이터의 콜백은 fire-and-forget 방식(재시도 없음)이므로,
 * 콜백이 유실되면 결제 상태를 영원히 알 수 없다.
 * 이 스케줄러가 콜백 유실의 유일한 안전망 역할을 한다.
 * </p>
 * <p>
 * 멱등성: {@link PaymentFacade#handleCallback}은 CAS 기반 상태 전이를 사용하므로,
 * 콜백과 폴링이 동시에 실행되어도 중복 처리가 방지된다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentPollingScheduler {

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;
    private final OrderFacade orderFacade;
    private final StockService stockService;

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    /**
     * REQUESTED 상태가 1분 이상 지속된 결제를 PG에 직접 조회하여 상태를 반영한다.
     * <p>
     * - 방금 생성된 결제는 콜백이 아직 오는 중일 수 있으므로 1분 유예한다.
     * - transactionKey가 있는 결제: transactionKey로 PG 조회.
     * - transactionKey가 없는 결제(고아): orderId로 PG 조회하여 복구 시도(Phase C).
     *   PG에 결제가 없으면 안전하게 FAILED 처리한다.
     * - 개별 건 실패 시에도 나머지 건의 처리를 계속한다.
     * - PG 연속 {@value #MAX_CONSECUTIVE_FAILURES}건 실패 시 사이클을 조기 종료하여
     *   PG 완전 장애 시 스레드 장시간 점유를 방지한다.
     * </p>
     */
    @Scheduled(fixedDelay = 60000)
    public void pollPendingPayments() {
        List<PaymentModel> pendingPayments = paymentService.findRequestedBeforeMinutesAgo(1);

        if (pendingPayments.isEmpty()) {
            return;
        }

        int recovered = 0;
        int orphanRecovered = 0;
        int orphanFailed = 0;
        int consecutiveFailures = 0;
        int total = pendingPayments.size();

        for (PaymentModel payment : pendingPayments) {
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                int processed = recovered + orphanRecovered + orphanFailed;
                log.warn("PG 연속 {}건 실패 → 사이클 조기 종료. 처리 {}/전체 {}, 잔여 {}건 다음 사이클에서 처리",
                        MAX_CONSECUTIVE_FAILURES, processed, total, total - processed);
                break;
            }
            try {
                if (payment.getTransactionKey() == null) {
                    // Phase C: transactionKey 없는 고아 → orderId로 PG 조회
                    boolean handled = recoverOrphanByOrderId(payment);
                    if (handled) {
                        orphanRecovered++;
                    } else {
                        orphanFailed++;
                    }
                } else {
                    // 기존: transactionKey로 PG 조회
                    boolean handled = recoverByTransactionKey(payment);
                    if (handled) {
                        recovered++;
                    }
                }
                consecutiveFailures = 0;
            } catch (CoreException e) {
                if (e.getErrorType() == ErrorType.PAYMENT_SERVICE_UNAVAILABLE) {
                    // CB OPEN — PG 장애 확정, 나머지 건도 실패할 것이므로 즉시 종료
                    int processed = recovered + orphanRecovered + orphanFailed;
                    log.warn("CircuitBreaker OPEN → 사이클 즉시 종료. 처리 {}/전체 {}, 잔여 {}건 다음 사이클에서 처리",
                            processed, total, total - processed);
                    break;
                }
                consecutiveFailures++;
                log.warn("폴링 실패 (연속 {}/{}): paymentId={}, error={}",
                        consecutiveFailures, MAX_CONSECUTIVE_FAILURES,
                        payment.getPaymentId(), e.getMessage());
            } catch (Exception e) {
                consecutiveFailures++;
                log.warn("폴링 실패 (연속 {}/{}): paymentId={}, error={}",
                        consecutiveFailures, MAX_CONSECUTIVE_FAILURES,
                        payment.getPaymentId(), e.getMessage());
            }
        }

        log.info("폴링 완료: {}/{} 복구, 고아 복구 {}건, 고아 FAILED {}건",
                recovered, total, orphanRecovered, orphanFailed);
    }

    /**
     * transactionKey로 PG 결제 상태를 조회하여 콜백을 복구한다.
     *
     * @return 복구 처리 여부
     */
    private boolean recoverByTransactionKey(PaymentModel payment) {
        GatewayPaymentResult pgResult = paymentGateway.getPaymentStatus(
                payment.getTransactionKey()
        );

        if (!pgResult.isPending()) {
            paymentFacade.handleCallback(
                    payment.getTransactionKey(),
                    pgResult.status(),
                    pgResult.reason()
            );
            return true;
        }
        return false;
    }

    /**
     * Phase C: orderId로 PG에 결제를 조회하여 고아 Payment를 복구한다.
     * <p>
     * PG에 결제가 존재하면 transactionKey를 매핑하고 콜백을 처리한다.
     * PG에 결제가 없으면 PG에 요청이 도달하지 않은 것이므로 안전하게 FAILED 처리한다.
     * </p>
     *
     * @return 복구 또는 FAILED 처리 여부
     */
    private boolean recoverOrphanByOrderId(PaymentModel payment) {
        List<GatewayPaymentResult> pgResults = paymentGateway.getPaymentsByOrderId(
                payment.getOrderId()
        );

        if (pgResults.isEmpty()) {
            // PG에 결제 없음 → PG에 요청 미도달 → 안전하게 FAILED + 재고 release
            paymentService.completePayment(
                    payment.getPaymentId(), PaymentStatus.FAILED, "PG 결제 없음 (orderId 조회)");
            releaseStocksForOrder(payment.getOrderId());
            if (!paymentService.hasActivePayment(payment.getOrderId())) {
                orderFacade.expireOrder(payment.getOrderId(),
                        RestoreReason.PAYMENT_FAILED, RestoreTriggerSource.PG_WEBHOOK);
            }
            log.info("고아 Payment FAILED + 재고 release: paymentId={}, orderId={} (PG에 결제 없음)",
                    payment.getPaymentId(), payment.getOrderId());
            return false;
        }

        // PG에 결제 존재 → 완료된 건 찾아서 복구
        GatewayPaymentResult completed = pgResults.stream()
                .filter(GatewayPaymentResult::isCompleted)
                .findFirst()
                .orElse(null);

        if (completed != null) {
            // transactionKey 매핑 + 콜백 처리
            paymentService.assignTransactionKey(
                    payment.getPaymentId(), completed.transactionKey());
            paymentFacade.handleCallback(
                    completed.transactionKey(),
                    completed.status(),
                    completed.reason()
            );
            log.info("고아 Payment 복구: paymentId={}, orderId={}, txnKey={}, status={}",
                    payment.getPaymentId(), payment.getOrderId(),
                    completed.transactionKey(), completed.status());
            return true;
        }

        // PG에 PENDING만 존재 → 아직 처리 중, 다음 폴링에서 재시도
        log.debug("고아 Payment 대기: paymentId={}, orderId={} (PG PENDING)",
                payment.getPaymentId(), payment.getOrderId());
        return false;
    }

    /**
     * 주문의 재고 hold를 release한다 (결제 실패/고아 정리 시).
     */
    private void releaseStocksForOrder(Long orderId) {
        List<OrderItemModel> orderItems = orderService.findOrderItems(orderId);
        for (OrderItemModel item : orderItems) {
            try {
                stockService.release(item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.warn("폴링 재고 release 실패: productId={}, qty={}",
                        item.getProductId(), item.getQuantity(), e);
            }
        }
    }
}

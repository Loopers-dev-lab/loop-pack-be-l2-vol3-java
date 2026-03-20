package com.loopers.application.payment;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentCompensationService;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 결제 Facade (퍼사드).
 * <p>
 * PaymentService, OrderService, StockService, PaymentGateway를
 * 조합(orchestration)하여 결제 비즈니스 플로우를 완성한다.
 * </p>
 *
 * <h3>TX 분리 패턴 (핵심 설계)</h3>
 * <p>
 * 클래스 레벨 {@code @Transactional}을 <b>의도적으로 적용하지 않는다</b>.
 * OrderFacade와 달리 외부 PG 호출(100~500ms)이 포함되므로,
 * 단일 트랜잭션으로 묶으면 DB 커넥션을 PG 응답 대기 시간 동안 점유하여
 * HikariCP 풀 고갈 위험이 있다.
 * </p>
 *
 * <h3>Optimistic Stock 패턴 (재고 hold 시점)</h3>
 * <p>
 * 주문 생성 시점에는 재고를 hold하지 않으며, 결제 요청 시 CAS hold를 수행한다.
 * hold 기간이 PG 응답 시간(수 초)으로 최소화되어 PG 장애 시 스노우볼을 방지한다.
 * </p>
 *
 * <pre>
 * ┌── TX-1: 주문 검증 + 재고 hold + Payment 생성 ─────────┐
 * │ DB 커넥션 점유: ~15ms → 즉시 반환                       │
 * └───────────────────────────────────────────────────────┘
 *        │
 * ┌── NO TX: PG 호출 (DB 커넥션 점유 0ms) ────────────────┐
 * │ 100~500ms 소요, DB 커넥션 사용 없음                     │
 * └───────────────────────────────────────────────────────┘
 *        │
 * ┌── TX-2: PG 응답 반영 (Service 내부 TX) ───────────────┐
 * │ DB 커넥션 점유: ~5ms → 즉시 반환                        │
 * └───────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>TX 분리 효과: DB 커넥션 점유 20ms (TX 안에 넣으면 500ms+) → 25배 차이</p>
 *
 * @see com.loopers.application.order.OrderFacade
 */
@Slf4j
@Service
@RequiredArgsConstructor
// ★ 클래스 레벨 @Transactional 없음! (TX 분리 패턴 — requestPayment에만 적용)
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentCompensationService compensationService;
    private final OrderService orderService;
    private final OrderFacade orderFacade;
    private final StockService stockService;
    private final PaymentGateway paymentGateway;

    @Value("${pg.callback-url:}")
    private String callbackUrl;

    /**
     * 결제를 요청한다.
     * <p>
     * TX 분리 패턴 적용:
     * TX-1(주문 검증 + Payment 생성) → NO TX(PG 호출) → TX-2(transactionKey 저장).
     * PG 호출 동안 DB 커넥션을 점유하지 않아 HikariCP 풀 고갈을 방지한다.
     * </p>
     *
     * @param userId   사용자 ID
     * @param orderId  주문 ID
     * @param cardType 카드 종류
     * @param cardNo   카드 번호
     * @return 결제 요청 결과 정보
     * @throws CoreException 주문이 결제 불가 상태이거나, 이미 결제 진행 중이거나, PG 오류 시
     */
    public PaymentInfo requestPayment(Long userId, Long orderId,
                                       CardType cardType, String cardNo) {
        // ━━ TX-1: 주문 검증 + 재고 hold + Payment 생성 (Service 내부 TX) ━━
        OrderModel order = orderService.findByIdAndUserId(orderId, userId);
        validatePayable(order);

        List<OrderItemModel> orderItems = orderService.findOrderItems(orderId);
        holdStocksForPayment(orderItems);

        PaymentModel payment = paymentService.createPayment(
                orderId, userId, cardType, cardNo, order.getTotalAmount()
        );
        // → TX-1 커밋, DB 커넥션 반환

        // ━━ NO TX: PG 외부 호출 (DB 커넥션 점유 없음) ━━
        try {
            GatewayPaymentResult pgResult = paymentGateway.requestPayment(
                    orderId, userId, cardType, cardNo,
                    order.getTotalAmount(), callbackUrl
            );

            // ━━ TX-2: transactionKey 저장 (Service 내부 TX) ━━
            PaymentModel updated = paymentService.assignTransactionKey(
                    payment.getPaymentId(), pgResult.transactionKey()
            );
            // → TX-2 커밋, DB 커넥션 반환

            return PaymentInfo.of(updated, pgResult);

        } catch (CoreException e) {
            // Phase A: Resilience4j fallback이 분류한 CoreException 기반 후처리
            if (e.getErrorType() == ErrorType.PAYMENT_PG_TIMEOUT) {
                // 타임아웃: PG에 요청 도달 → 돈 빠졌을 수 있음 → hold 유지 (폴링이 처리)
                log.warn("PG 타임아웃 — Payment REQUESTED + 재고 hold 유지. paymentId={}, orderId={}",
                        payment.getPaymentId(), orderId);
            } else {
                // CB OPEN(503), PG 에러(502) 등: 확실한 실패 → 즉시 FAILED + 재고 release
                releaseStocksForPayment(orderItems);
                paymentService.completePayment(
                        payment.getPaymentId(), PaymentStatus.FAILED, e.getMessage());
                log.info("PG 실패 → Payment 즉시 FAILED + 재고 release. paymentId={}, errorType={}",
                        payment.getPaymentId(), e.getErrorType());
            }
            throw e;
        } catch (Exception e) {
            // 예상치 못한 예외: 안전하게 FAILED + 재고 release
            releaseStocksForPayment(orderItems);
            paymentService.completePayment(
                    payment.getPaymentId(), PaymentStatus.FAILED, e.getMessage());
            log.warn("PG 예상치 못한 에러 → Payment 즉시 FAILED + 재고 release. paymentId={}",
                    payment.getPaymentId(), e);
            throw new CoreException(ErrorType.PAYMENT_PG_ERROR, e.getMessage());
        }
    }

    /**
     * PG 콜백을 수신하여 결제 결과를 처리한다.
     * <p>
     * 결제 성공 시 주문 항목별로 예약된 재고를 확정(commit)한다.
     * CAS 기반 상태 전이로 중복 콜백을 멱등 처리한다(이미 처리된 경우 무시).
     * </p>
     *
     * @param transactionKey PG 트랜잭션 식별자
     * @param status         PG 결제 결과 상태 ("SUCCESS" / "FAILED")
     * @param reason         실패 사유 (성공 시 null)
     * @throws CoreException transactionKey에 해당하는 결제가 없을 때 (PAYMENT_NOT_FOUND)
     */
    @Transactional
    public void handleCallback(String transactionKey, String status, String reason) {
        PaymentModel payment = paymentService.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        PaymentStatus toStatus = "SUCCESS".equals(status)
                ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        boolean updated = paymentService.completePayment(
                payment.getPaymentId(), toStatus, reason
        );

        if (!updated) {
            return; // 이미 처리된 콜백 → 멱등 처리
        }

        if (toStatus == PaymentStatus.SUCCESS) {
            // 결제 성공 → 예약 재고 확정 (productId 오름차순으로 데드락 방지)
            List<OrderItemModel> orderItems = orderService.findOrderItems(payment.getOrderId());
            List<OrderItemModel> sorted = orderItems.stream()
                    .sorted(Comparator.comparing(OrderItemModel::getProductId))
                    .toList();
            try {
                for (OrderItemModel item : sorted) {
                    stockService.commit(item.getProductId(), item.getQuantity());
                }
                boolean orderMarked = orderService.markAsPaid(payment.getOrderId());
                if (!orderMarked) {
                    log.warn("주문 PAID 전이 실패 — 이미 만료/취소됨. orderId={}", payment.getOrderId());
                }
            } catch (Exception e) {
                // 별도 TX(REQUIRES_NEW)로 보정 테이블 기록 — 현재 TX 롤백에 영향받지 않음
                compensationService.recordFailedCommit(
                        payment.getPaymentId(), payment.getOrderId(), e.getMessage());
                log.error("Stock commit 실패 — 보정 기록 생성. paymentId={}, orderId={}",
                        payment.getPaymentId(), payment.getOrderId(), e);
                throw e; // TX 롤백 → Payment CAS도 롤백 → REQUESTED 유지
            }
        }

        // FAILED 시: 이 결제가 hold한 재고 release + 모든 결제 terminal이면 주문 만료
        if (toStatus == PaymentStatus.FAILED) {
            List<OrderItemModel> failedOrderItems = orderService.findOrderItems(payment.getOrderId());
            releaseStocksForPayment(failedOrderItems);

            boolean hasOtherActive = paymentService.hasActivePayment(payment.getOrderId());
            if (!hasOtherActive) {
                orderFacade.expireOrder(
                        payment.getOrderId(),
                        RestoreReason.PAYMENT_FAILED,
                        RestoreTriggerSource.PG_WEBHOOK
                );
                log.info("결제 전체 실패 → 주문 즉시 만료 + 재고 release. orderId={}", payment.getOrderId());
            }
        }
    }

    /**
     * 결제 정보를 조회한다. 본인 결제만 조회 가능.
     *
     * @param paymentId 결제 ID
     * @param userId    사용자 ID
     * @return 결제 엔티티
     * @throws CoreException 결제가 존재하지 않거나 본인 결제가 아닌 경우 (PAYMENT_NOT_FOUND)
     */
    public PaymentModel getPaymentByIdAndUser(Long paymentId, Long userId) {
        PaymentModel payment = paymentService.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    /**
     * 결제 가능 여부를 검증한다.
     * <p>
     * PENDING_PAYMENT 상태이고, 시간 만료되지 않았으며,
     * 진행 중인 결제가 없는 경우에만 결제 가능.
     * </p>
     *
     * @param order 주문 엔티티
     * @throws CoreException 결제 불가 상태일 때 (PAYMENT_NOT_PAYABLE, PAYMENT_ALREADY_IN_PROGRESS)
     */
    private void validatePayable(OrderModel order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.PAYMENT_NOT_PAYABLE);
        }
        if (order.isTimeExpired()) {
            throw new CoreException(ErrorType.PAYMENT_NOT_PAYABLE, "주문이 만료되었습니다");
        }
        if (paymentService.hasActivePayment(order.getOrderId())) {
            throw new CoreException(ErrorType.PAYMENT_ALREADY_IN_PROGRESS);
        }
    }

    /**
     * 결제 직전 재고 hold (productId 오름차순, 데드락 방지).
     * hold 실패 시 이미 hold된 재고를 역순으로 release하는 보상 로직 포함.
     */
    private void holdStocksForPayment(List<OrderItemModel> orderItems) {
        List<OrderItemModel> sorted = orderItems.stream()
                .sorted(Comparator.comparing(OrderItemModel::getProductId))
                .toList();
        int heldCount = 0;
        try {
            for (OrderItemModel item : sorted) {
                stockService.hold(item.getProductId(), item.getQuantity());
                heldCount++;
            }
        } catch (CoreException e) {
            for (int i = heldCount - 1; i >= 0; i--) {
                stockService.release(sorted.get(i).getProductId(), sorted.get(i).getQuantity());
            }
            throw e;
        }
    }

    /**
     * 결제 실패 시 hold된 재고를 즉시 release (productId 오름차순).
     * 개별 release 실패 시에도 나머지 상품의 release를 계속 수행한다.
     */
    private void releaseStocksForPayment(List<OrderItemModel> orderItems) {
        List<OrderItemModel> sorted = orderItems.stream()
                .sorted(Comparator.comparing(OrderItemModel::getProductId))
                .toList();
        for (OrderItemModel item : sorted) {
            try {
                stockService.release(item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.warn("결제 실패 후 재고 release 실패: productId={}, qty={}",
                        item.getProductId(), item.getQuantity(), e);
            }
        }
    }
}

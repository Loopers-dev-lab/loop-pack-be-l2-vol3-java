package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentDomainService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트랜잭션 경계가 필요한 결제 로직을 분리.
 * Spring @Transactional은 프록시 기반이라 self-invocation이 안 되므로,
 * PaymentApplicationService에서 외부 호출(PG)과 분리된 트랜잭션 메서드를 호출할 수 있도록 별도 Bean으로 분리.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentTransactionHelper {

    private final PaymentDomainService paymentDomainService;
    private final OrderDomainService orderDomainService;
    private final ProductStockDomainService productStockDomainService;
    private final CouponIssueDomainService couponIssueDomainService;

    /**
     * TX1: Payment(PENDING) 생성 + Order(PAYMENT_PENDING) 전환.
     * 이 트랜잭션이 커밋된 후 PG 호출을 수행한다.
     * 단일 트랜잭션에서 Payment aggregate(생성)와 Order aggregate(상태 변경)를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 결제 시작과 주문 상태 전환은 원자적으로 처리되어야 한다.
     */
    @Transactional
    public Payment initializePayment(Long orderId, Long userId, CardType cardType, String cardNo) {
        // 비관적 락으로 Order를 조회하여 동시 결제 요청을 직렬화
        Order order = orderDomainService.getByIdAndUserIdForUpdate(orderId, userId);

        // 이미 진행 중인 결제가 있으면 중복 결제 차단
        boolean hasActivePayment = paymentDomainService.getByOrderId(orderId).stream()
            .anyMatch(p -> p.getStatus() == PaymentStatus.PENDING
                || p.getStatus() == PaymentStatus.IN_PROGRESS);
        if (hasActivePayment) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제가 있습니다.");
        }

        // 재결제 시 재고/쿠폰 재선점 (실패 시 이미 복원되었으므로 다시 차감)
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            Order orderWithItems = orderDomainService.getByIdWithItems(orderId);
            reserveStockAndCoupon(orderWithItems, userId);
        }

        int amount = order.getTotalPrice().amount();
        Payment payment = paymentDomainService.createPayment(orderId, userId, cardType, cardNo, amount);
        order.startPayment();

        return payment;
    }

    /**
     * TX2: PG 응답의 transactionKey를 Payment에 반영.
     * PENDING → IN_PROGRESS
     */
    @Transactional
    public Payment markPaymentInProgress(Long orderId, Long userId, String transactionKey) {
        List<Payment> payments = paymentDomainService.getByOrderId(orderId);
        Payment payment = payments.stream()
            .filter(p -> p.getUserId().equals(userId) && p.getStatus() == PaymentStatus.PENDING)
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "PENDING 상태의 결제 정보를 찾을 수 없습니다."));
        payment.markInProgress(transactionKey);
        return paymentDomainService.save(payment);
    }

    /**
     * TX3: 콜백/조회 결과를 Payment + Order에 반영.
     * FAILED 시 재고/쿠폰도 자동 복원한다.
     * 단일 트랜잭션에서 Payment, Order, ProductStock, CouponIssue aggregate를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 결제 결과 반영과 보상 복원은 원자적으로 일관성을 유지해야 한다.
     */
    @Transactional
    public Payment applyPaymentResult(String transactionKey, String status, String reason) {
        Payment payment = paymentDomainService.getByTransactionKeyForUpdate(transactionKey);

        // 이미 최종 상태이면 멱등하게 처리 (재고/쿠폰 중복 복원 방지)
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        Order order = orderDomainService.getByIdWithItems(payment.getOrderId());

        if ("SUCCESS".equals(status)) {
            payment.markPaid();
            order.completePayment();
        } else if ("FAILED".equals(status)) {
            payment.markFailed(reason);
            order.failPayment();
            restoreStockAndCoupon(order);
        } else {
            log.warn("[콜백 상태 미인식] transactionKey={}, status={}, 상태 전이 없이 유지합니다.",
                transactionKey, status);
        }

        return payment;
    }

    /**
     * PENDING Payment를 PG 조회 결과로 복구한다.
     * 비관적 락(FOR UPDATE)으로 PENDING row를 잠가 동시 sync/콜백 진입을 직렬화한다.
     * 이미 복구된 경우(콜백이 먼저 도착) 멱등하게 현재 상태를 반환한다.
     */
    @Transactional
    public Payment recoverPendingPayment(Long orderId, Long userId, String transactionKey, String pgStatus, String reason) {
        // 비관적 락으로 PENDING Payment를 잠금
        List<Payment> lockedPending = paymentDomainService.findPendingByOrderIdAndUserIdForUpdate(orderId, userId);

        if (lockedPending.isEmpty()) {
            // 이미 복구됨(콜백이 먼저 도착) → transactionKey로 해당 결제를 재조회
            Payment recovered = paymentDomainService.findByTransactionKey(transactionKey)
                .orElseThrow(() -> {
                    log.error("[정합성 사고] orderId={}, transactionKey={}, PENDING 없고 transactionKey로도 조회 불가",
                        orderId, transactionKey);
                    return new CoreException(ErrorType.INTERNAL_ERROR,
                        "내부 결제 상태가 비정상입니다. 고객센터에 문의해주세요.");
                });
            // 방어적 검증: 같은 주문/사용자인지 확인
            if (!recovered.getOrderId().equals(orderId) || !recovered.getUserId().equals(userId)) {
                log.error("[정합성 사고] transactionKey={}, 기대 orderId={}/userId={}, 실제 orderId={}/userId={}",
                    transactionKey, orderId, userId, recovered.getOrderId(), recovered.getUserId());
                throw new CoreException(ErrorType.INTERNAL_ERROR,
                    "내부 결제 상태가 비정상입니다. 고객센터에 문의해주세요.");
            }
            return recovered;
        }

        if (lockedPending.size() > 1) {
            log.error("[정합성 사고] orderId={}, userId={}, PENDING 결제 {}건. 자동 복구 불가.",
                orderId, userId, lockedPending.size());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "내부 결제 상태가 비정상입니다. 고객센터에 문의해주세요.");
        }

        Payment payment = lockedPending.get(0);
        payment.markInProgress(transactionKey);

        if ("SUCCESS".equals(pgStatus)) {
            Order order = orderDomainService.getByIdWithItems(payment.getOrderId());
            payment.markPaid();
            order.completePayment();
        } else if ("FAILED".equals(pgStatus)) {
            Order order = orderDomainService.getByIdWithItems(payment.getOrderId());
            payment.markFailed(reason);
            order.failPayment();
            restoreStockAndCoupon(order);
        }
        // PG도 PENDING이면 IN_PROGRESS까지만 전환

        return paymentDomainService.save(payment);
    }

    /**
     * PG 미접수 확정 시 PENDING Payment를 실패 처리하고 재고/쿠폰을 복원한다.
     * PG 조회가 성공적으로 0건을 반환한 경우에만 호출해야 한다.
     */
    @Transactional
    public Payment cancelPendingPayment(Long orderId, Long userId, String reason) {
        List<Payment> lockedPending = paymentDomainService.findPendingByOrderIdAndUserIdForUpdate(orderId, userId);

        if (lockedPending.isEmpty()) {
            // 이미 처리됨 → 멱등하게 반환
            return paymentDomainService.getByOrderId(orderId).stream()
                .filter(p -> p.getUserId().equals(userId))
                .reduce((first, second) -> second) // 가장 최근
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        }

        if (lockedPending.size() > 1) {
            log.error("[정합성 사고] orderId={}, userId={}, PENDING 결제 {}건. 자동 처리 불가.",
                orderId, userId, lockedPending.size());
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "내부 결제 상태가 비정상입니다. 고객센터에 문의해주세요.");
        }

        Payment payment = lockedPending.get(0);
        payment.markFailed(reason);

        Order order = orderDomainService.getByIdWithItems(payment.getOrderId());
        order.failPayment();
        restoreStockAndCoupon(order);

        return paymentDomainService.save(payment);
    }

    /**
     * 재고/쿠폰 복원. deadlock 방지를 위해 productId 기준 정렬.
     */
    private void restoreStockAndCoupon(Order order) {
        List<OrderItem> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .toList();
        for (OrderItem item : sortedItems) {
            productStockDomainService.restoreWithLock(item.getProductId(), item.getQuantity().value());
        }
        if (order.getCouponIssueId() != null) {
            couponIssueDomainService.restoreCoupon(order.getCouponIssueId());
        }
    }

    /**
     * 재결제 시 재고/쿠폰 재선점. deadlock 방지를 위해 productId 기준 정렬.
     * PAYMENT_FAILED 후 같은 주문에 대해 새 결제를 시작할 때,
     * 이미 복원된 재고/쿠폰을 다시 차감한다.
     */
    private void reserveStockAndCoupon(Order order, Long userId) {
        List<OrderItem> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .toList();
        for (OrderItem item : sortedItems) {
            productStockDomainService.deductWithLock(item.getProductId(), item.getQuantity().value());
        }
        if (order.getCouponIssueId() != null) {
            couponIssueDomainService.useCoupon(order.getCouponIssueId(), userId);
        }
    }
}

package com.loopers.application.payment;

import com.loopers.application.order.OrderAppService;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.payment.PgPaymentStatusResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentAppService paymentAppService;
    private final OrderAppService orderAppService;
    private final PgClient pgClient;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    private static final String PAYMENT_LOCK_PREFIX = "payment:lock:order:";
    private static final long LOCK_LEASE_TIME_SECONDS = 15;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    public PaymentInfo requestPayment(Long userId, Long orderId, String cardType, String cardNo) {
        RLock lock = redissonClient.getLock(PAYMENT_LOCK_PREFIX + orderId);
        boolean acquired;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 락 획득 중 인터럽트 발생");
        }
        if (!acquired) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중인 주문입니다.");
        }
        try {
            // 1. 주문 존재/소유자 검증
            Order order = orderAppService.getById(orderId);
            order.validateOwner(userId);
            Money paymentAmount = order.getPaymentAmount();

            // 2. TX1: PENDING 저장 → 커밋
            Payment payment = paymentAppService.createPayment(orderId, userId, cardType, cardNo, paymentAmount);

            // 3. TX 밖에서 PG 호출 (DB 커넥션 미점유)
            PgPaymentCommand command = new PgPaymentCommand(
                    orderId, userId, cardType, cardNo, paymentAmount.getAmount(), callbackUrl
            );
            PgPaymentResult pgResult = pgClient.requestPayment(command);

            log.info("PG 결제 요청 결과: orderId={}, accepted={}, message={}", orderId, pgResult.accepted(), pgResult.message());

            // accepted 여부와 관계없이 PENDING 상태로 응답 (콜백으로 최종 확정)
            return PaymentInfo.from(payment);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public PaymentInfo handleCallback(Long orderId, String transactionId, String status, String message) {
        RLock lock = redissonClient.getLock(PAYMENT_LOCK_PREFIX + orderId);
        boolean acquired;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "콜백 락 획득 중 인터럽트 발생");
        }
        if (!acquired) {
            Payment payment = paymentAppService.getByOrderIdAndActiveStatus(orderId);
            return PaymentInfo.from(payment);
        }
        try {
            // 1. 결제 조회
            Payment payment = paymentAppService.getByOrderIdAndActiveStatus(orderId);

            // 2. 이미 터미널 상태면 멱등하게 반환
            if (payment.getStatus().isTerminal()) {
                return PaymentInfo.from(payment);
            }

            // 3. 상태에 따라 처리
            if ("SUCCESS".equals(status)) {
                // TX2: Payment → SUCCESS
                payment = paymentAppService.completePayment(payment.getId(), transactionId, message);
                // TX3: Order → PAID
                orderAppService.pay(payment.getOrderId());
                // 결제 완료 이벤트 발행
                eventPublisher.publishEvent(new PaymentCompletedEvent(
                        payment.getId(), payment.getOrderId(), payment.getUserId(), ZonedDateTime.now()));
            } else {
                // TX2: Payment → FAIL
                payment = paymentAppService.failPayment(payment.getId(), message);
            }

            return PaymentInfo.from(payment);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public List<PaymentInfo> syncPendingPayments() {
        List<Payment> pendingPayments = paymentAppService.getPendingPayments();
        List<PaymentInfo> results = new ArrayList<>();
        for (Payment payment : pendingPayments) {
            try {
                results.add(syncSinglePayment(payment));
            } catch (Exception e) {
                log.error("결제 동기화 실패: paymentId={}, orderId={}", payment.getId(), payment.getOrderId(), e);
            }
        }
        return results;
    }

    private PaymentInfo syncSinglePayment(Payment payment) {
        RLock lock = redissonClient.getLock(PAYMENT_LOCK_PREFIX + payment.getOrderId());
        boolean acquired;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "동기화 락 획득 중 인터럽트 발생");
        }
        if (!acquired) {
            log.info("동기화 락 획득 실패 (다른 처리 진행 중), 스킵: orderId={}", payment.getOrderId());
            return PaymentInfo.from(payment);
        }
        try {
            PgPaymentStatusResult pgStatus = pgClient.getPaymentStatus(payment.getOrderId(), payment.getUserId());

            // PG 응답이 UNKNOWN이면 아직 확정 불가 → PENDING 유지
            if ("UNKNOWN".equals(pgStatus.status())) {
                log.info("PG 상태 미확정, PENDING 유지: orderId={}", payment.getOrderId());
                return PaymentInfo.from(payment);
            }

            if ("SUCCESS".equals(pgStatus.status())) {
                payment = paymentAppService.completePayment(payment.getId(), pgStatus.transactionId(), pgStatus.message());
                orderAppService.pay(payment.getOrderId());
            } else if ("FAIL".equals(pgStatus.status())) {
                payment = paymentAppService.failPayment(payment.getId(), pgStatus.message());
            }

            return PaymentInfo.from(payment);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

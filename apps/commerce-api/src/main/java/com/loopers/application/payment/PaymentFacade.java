package com.loopers.application.payment;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.*;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final MemberService memberService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PgPaymentGateway pgPaymentGateway;

    @Value("${pg.simulator.callback-url}")
    private String callbackUrl;

    /**
     * 결제 요청
     * 1) 회원 인증 → 2) 주문 확인 → 3) Payment 생성(PENDING, 트랜잭션1)
     * 4) PG 요청(트랜잭션 밖) → 5) 상태 반영(트랜잭션2)
     * 외부 호출을 트랜잭션 밖에서 실행하여 DB 커넥션 점유를 최소화한다.
     */
    public PaymentInfo requestPayment(String loginId, String password,
                                      PaymentV1Dto.PaymentRequest request) {
        // 1. 회원 인증
        MemberModel member = memberService.getMyInfo(loginId, password);

        // 2. 주문 확인 + 본인 주문 검증
        OrderModel order = orderService.getById(Long.parseLong(request.orderId()));
        if (!order.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 결제할 수 있습니다.");
        }

        // 3. PaymentModel 생성 (PENDING 상태) — 트랜잭션 1
        PaymentModel payment = paymentService.create(
                order.getId(), member.getId(), order.getTotalAmount(),
                request.cardType(), request.cardNo()
        );

        // 4. PG에 결제 요청 — 트랜잭션 밖 (DB 커넥션 미점유)
        PgPaymentResponse pgResponse = pgPaymentGateway.requestPayment(
                String.valueOf(member.getId()),
                new PgPaymentRequest(
                        request.orderId(),
                        request.cardType(),
                        request.cardNo(),
                        order.getTotalAmount(),
                        callbackUrl
                )
        );

        // 5. PG 응답 반영 — 트랜잭션 2
        if (!"SUCCESS".equals(pgResponse.meta().result())) {
            if ("PG_UNAVAILABLE".equals(pgResponse.meta().errorCode())) {
                paymentService.markTimedOut(payment.getId(), null);
            } else {
                paymentService.markFailed(payment.getId(), pgResponse.meta().message());
            }
        } else {
            paymentService.assignTransactionId(payment.getId(), pgResponse.data().transactionKey());
        }

        return PaymentInfo.from(paymentService.getById(payment.getId()));
    }

    /**
     * 콜백 수신
     * PG가 결제 처리(1~5초) 완료 후 호출
     * 외부 호출 없이 DB 작업만 수행하므로 단일 트랜잭션 유지
     * 중복 콜백 방어: 이미 최종 상태(SUCCESS/FAILED)이면 상태 전이 검증에서 예외 발생
     * @Version(낙관적 락)으로 콜백과 수동복구 동시 호출 시 하나만 성공
     */
    @Transactional
    public void handleCallback(PaymentV1Dto.CallbackRequest request) {
        PaymentModel payment = paymentService.getByTransactionId(request.transactionKey());

        if ("SUCCESS".equals(request.status())) {
            payment.markSuccess(request.transactionKey());
        } else {
            payment.markFailed(request.reason());
        }
    }

    /**
     * 결제 조회 + 수동 복구
     * PENDING/TIMED_OUT 상태인 경우 PG에 직접 상태를 조회해서 동기화
     * 외부 호출을 트랜잭션 밖에서 실행하여 DB 커넥션 점유를 최소화한다.
     */
    public PaymentInfo syncPaymentStatus(String loginId, String password, Long paymentId) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        PaymentModel payment = paymentService.getById(paymentId);

        // 본인 결제 검증
        if (!payment.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 결제만 조회할 수 있습니다.");
        }

        // 이미 최종 상태면 조회만
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentInfo.from(payment);
        }

        // PG에 상태 확인 — 트랜잭션 밖 (DB 커넥션 미점유)
        String memberId = String.valueOf(payment.getMemberId());
        if (payment.getTransactionId() != null) {
            PgPaymentStatusResponse pgStatus = pgPaymentGateway.getPaymentStatus(memberId, payment.getTransactionId());
            updatePaymentFromPgStatus(payment.getId(), pgStatus);
        } else {
            PgOrderResponse pgOrder = pgPaymentGateway.getPaymentByOrderId(memberId, String.valueOf(payment.getOrderId()));
            updatePaymentFromPgOrder(payment.getId(), pgOrder);
        }

        return PaymentInfo.from(paymentService.getById(paymentId));
    }

    /**
     * 정체된 결제 자동 복구
     * PENDING/TIMED_OUT 상태로 남아있는 결제건을 PG에 조회하여 상태를 동기화한다.
     * 개별 결제 실패가 전체 복구를 중단시키지 않도록 건별로 예외를 처리한다.
     */
    public void recoverStalledPayments() {
        for (PaymentModel payment : paymentService.findStalledPayments()) {
            try {
                String memberId = String.valueOf(payment.getMemberId());
                if (payment.getTransactionId() != null) {
                    PgPaymentStatusResponse pgStatus = pgPaymentGateway.getPaymentStatus(memberId, payment.getTransactionId());
                    updatePaymentFromPgStatus(payment.getId(), pgStatus);
                } else {
                    PgOrderResponse pgOrder = pgPaymentGateway.getPaymentByOrderId(memberId, String.valueOf(payment.getOrderId()));
                    updatePaymentFromPgOrder(payment.getId(), pgOrder);
                }
            } catch (Exception e) {
                log.warn("결제 복구 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

    private void updatePaymentFromPgOrder(Long paymentId, PgOrderResponse pgOrder) {
        if (pgOrder.data() == null || pgOrder.data().transactions() == null || pgOrder.data().transactions().isEmpty()) {
            return;
        }
        // 가장 최근 거래(마지막) 기준으로 상태 반영
        PgOrderResponse.Transaction latest = pgOrder.data().transactions().getLast();
        if ("SUCCESS".equals(latest.status())) {
            paymentService.markSuccess(paymentId, latest.transactionKey());
        } else if ("FAILED".equals(latest.status())) {
            paymentService.markFailed(paymentId, latest.reason());
        }
    }

    private void updatePaymentFromPgStatus(Long paymentId, PgPaymentStatusResponse pgStatus) {
        if (pgStatus.data() == null) {
            return;
        }

        String status = pgStatus.data().status();
        if ("SUCCESS".equals(status)) {
            paymentService.markSuccess(paymentId, pgStatus.data().transactionKey());
        } else if ("FAILED".equals(status)) {
            paymentService.markFailed(paymentId, pgStatus.data().reason());
        }
        // PENDING이면 아직 처리 중이므로 아무것도 안 함
    }
}

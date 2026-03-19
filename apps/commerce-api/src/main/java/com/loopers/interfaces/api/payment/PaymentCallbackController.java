package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 콜백 수신 컨트롤러
 *
 * PG 시뮬레이터가 결제 처리 완료 후 이 엔드포인트로 HTTP POST를 보낸다.
 * 인증(@AuthUser) 없음 — PG 서버가 호출하므로 사용자 인증 대신 PG 조회 API로 검증한다.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final PaymentFacade paymentFacade;

    public PaymentCallbackController(PaymentFacade paymentFacade) {
        this.paymentFacade = paymentFacade;
    }

    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(
            @RequestBody PaymentRequest.PgCallbackRequest request,
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader) {
        log.info("PG 콜백 수신: txnKey={}, orderId={}, status={}, reason={}",
                request.transactionKey(), request.orderId(), request.status(), request.reason());

        Long userId = parseUserId(userIdHeader);

        paymentFacade.processCallback(request.transactionKey(), request.orderId(), userId);

        return ApiResponse.success();
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

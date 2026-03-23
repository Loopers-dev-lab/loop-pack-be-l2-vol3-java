package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCallbackCommand;
import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @AuthUser AuthUserPrincipal user,
        @PathVariable Long orderId,
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentCommand command = new PaymentCommand(
            user.getId(),
            orderId,
            request.cardType(),
            request.cardNo()
        );
        PaymentInfo paymentInfo = paymentFacade.requestPayment(command);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentInfo));
    }

    @PostMapping("/payments/callback")
    public ApiResponse<PaymentV1Dto.PaymentResponse> handleCallback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        log.info("PG 콜백 수신. orderId={}, status={}", request.orderId(), request.status());

        PaymentCallbackCommand command = new PaymentCallbackCommand(
            Long.parseLong(request.orderId()),
            request.transactionKey(),
            request.status(),
            request.reason()
        );
        PaymentInfo paymentInfo = paymentFacade.handleCallback(command);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentInfo));
    }
}

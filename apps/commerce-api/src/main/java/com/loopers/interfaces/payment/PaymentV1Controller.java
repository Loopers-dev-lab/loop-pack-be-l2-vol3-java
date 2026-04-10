package com.loopers.interfaces.payment;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentStatusResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.payment.dto.PaymentRequest;
import com.loopers.interfaces.payment.dto.PaymentResponse;
import com.loopers.interfaces.payment.dto.PgCallbackRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final OrderFacade orderFacade;
    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentResponse.OrderPaymentSummary> requestPayment(
            @Login LoginUser loginUser,
            @Valid @RequestBody PaymentRequest.Create request
    ) {
        OrderResult.OrderPaymentSummary result = orderFacade.createOrderWithPayment(
                loginUser.id(), request.toOrderCriteria());

        return ApiResponse.success(PaymentResponse.OrderPaymentSummary.from(result));
    }

    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(
            @RequestBody PgCallbackRequest request
    ) {
        paymentFacade.handleCallback(
                request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }

    @GetMapping("/status")
    public ApiResponse<PaymentResponse.PaymentStatus> getPaymentStatus(
            @Login LoginUser loginUser,
            @RequestParam Long orderId
    ) {
        PaymentStatusResult result = paymentFacade.getPaymentStatus(orderId);
        return ApiResponse.success(PaymentResponse.PaymentStatus.from(result));
    }
}

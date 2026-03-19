package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentDto.PaymentResponse> requestPayment(
            @LoginUser Member member,
            @RequestBody PaymentDto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(
                member.getId(), request.orderId(), request.cardType(), request.cardNo()
        );
        return ApiResponse.success(PaymentDto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    public ApiResponse<PaymentDto.PaymentResponse> handleCallback(
            @RequestBody PaymentDto.CallbackRequest request
    ) {
        PaymentInfo info = paymentFacade.handleCallback(
                request.parseOrderId(), request.transactionKey(), request.status(), request.message()
        );
        return ApiResponse.success(PaymentDto.PaymentResponse.from(info));
    }

    @PostMapping("/sync")
    public ApiResponse<PaymentDto.PaymentListResponse> syncPendingPayments() {
        List<PaymentInfo> infoList = paymentFacade.syncPendingPayments();
        return ApiResponse.success(PaymentDto.PaymentListResponse.from(infoList));
    }
}

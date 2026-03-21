package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentQueryApplicationService;
import com.loopers.application.payment.PaymentUseCase;
import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.domain.member.Member;
import com.loopers.domain.payment.Payment;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentUseCase paymentUseCase;
    private final PaymentQueryApplicationService paymentQueryApplicationService;

    @Value("${loopers.payment.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private String callbackUrl;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentDto.PaymentResponse> startPayment(
            @AuthMember Member member,
            @Valid @RequestBody PaymentDto.StartPaymentRequest request
    ) {
        Payment payment = paymentUseCase.start(
                member.id().value(),
                request.orderId(),
                request.cardType(),
                request.cardNo(),
                callbackUrl
        );
        return ApiResponse.success(PaymentDto.PaymentResponse.from(payment));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<PaymentDto.PaymentResponse> cancelPayment(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        Payment payment = paymentUseCase.cancel(new CancelPaymentCommand(member.id().value(), orderId));
        return ApiResponse.success(PaymentDto.PaymentResponse.from(payment));
    }

    @PostMapping("/{orderId}/reconcile")
    public ApiResponse<PaymentDto.PaymentResponse> reconcilePayment(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        Payment payment = paymentUseCase.reconcile(member.id().value(), orderId);
        return ApiResponse.success(PaymentDto.PaymentResponse.from(payment));
    }

    @GetMapping
    public ApiResponse<PaymentDto.PaymentListResponse> getPayments(
            @AuthMember Member member,
            @RequestParam UUID orderId
    ) {
        List<Payment> payments = paymentQueryApplicationService.getPaymentsByOrder(member.id().value(), orderId);
        return ApiResponse.success(PaymentDto.PaymentListResponse.from(orderId, payments));
    }
}

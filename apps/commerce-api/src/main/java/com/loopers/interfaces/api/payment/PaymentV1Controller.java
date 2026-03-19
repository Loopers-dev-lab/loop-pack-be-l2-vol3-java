package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;
    private final UserFacade userFacade;

    public PaymentV1Controller(PaymentFacade paymentFacade, UserFacade userFacade) {
        this.paymentFacade = paymentFacade;
        this.userFacade = userFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
            @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = paymentFacade.requestPayment(
                userId,
                request.orderId(),
                request.cardType(),
                request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}

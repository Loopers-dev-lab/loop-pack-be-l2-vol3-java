package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCompletionApplicationService;
import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentCallbackController {

    private final PaymentCompletionApplicationService paymentCompletionApplicationService;

    @PostMapping("/callback")
    public ApiResponse<Void> callback(
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader,
            @RequestBody PaymentCallbackDto.CallbackRequest request
    ) {
        String resolvedUserId = request.memberId();
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            resolvedUserId = userIdHeader;
        }
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "콜백 처리에 필요한 회원 ID가 없습니다.");
        }

        paymentCompletionApplicationService.complete(
                new CompletePaymentCommand(resolvedUserId, request.transactionKey())
        );
        return ApiResponse.success();
    }
}

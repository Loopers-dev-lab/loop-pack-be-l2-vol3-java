package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제(Payment) 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>결제 요청, PG 콜백 수신, 결제 상태 조회 기능을 제공한다.
 * 결제 요청과 조회는 고객 인증이 필요하며, PG 콜백은 인증 없이 수신한다.
 * 모든 처리는 {@link PaymentFacade}를 통해 위임한다.</p>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    /**
     * 결제를 요청한다.
     *
     * <p>주문에 대해 PG 결제를 요청한다. TX 분리 패턴으로 DB 커넥션 풀을 보호한다.</p>
     *
     * @param user    인증된 사용자 (인터셉터에서 주입)
     * @param request 결제 요청 DTO (주문 ID, 카드 종류, 카드 번호)
     * @return 결제 요청 결과 (HTTP 200)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> requestPayment(
            @AuthUser UserModel user,
            @Valid @RequestBody PaymentV1Dto.PaymentRequest request) {
        PaymentInfo info = paymentFacade.requestPayment(
                user.getUserId(), request.orderId(),
                request.cardType(), request.cardNo()
        );
        return ResponseEntity.ok(ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info)));
    }

    /**
     * PG 콜백을 수신한다.
     *
     * <p>PG 시뮬레이터에서 결제 결과를 비동기로 전달한다. 인증 없이 수신한다.</p>
     *
     * @param request 콜백 요청 DTO (transactionKey, status, reason)
     * @return 성공 응답 (HTTP 200)
     */
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Object>> handleCallback(
            @RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(
                request.transactionKey(), request.status(), request.reason()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 결제 상태를 조회한다.
     *
     * @param user      인증된 사용자 (인터셉터에서 주입)
     * @param paymentId 결제 ID
     * @return 결제 정보 응답 (HTTP 200)
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> getPayment(
            @AuthUser UserModel user,
            @PathVariable Long paymentId) {
        PaymentModel payment = paymentFacade.getPaymentByIdAndUser(paymentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(
                PaymentV1Dto.PaymentResponse.from(PaymentInfo.from(payment))
        ));
    }
}

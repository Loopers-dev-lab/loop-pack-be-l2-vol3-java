package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "결제 API", description = "결제 요청 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 PG에 요청합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "결제 요청 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<PaymentResponse>> requestPayment(
            @Valid @RequestBody PaymentRequest request
    );

    @Operation(summary = "PG 결제 콜백 수신", description = "PG에서 결제 처리 결과를 콜백으로 수신합니다. 처리 실패 시 500을 반환해 PG 재시도를 유도합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "콜백 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알 수 없는 pgTransactionKey"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "처리 실패 — PG 재시도")
    })
    ResponseEntity<ApiResponse<Void>> handleCallback(
            @RequestBody PgCallbackRequest request
    );
}

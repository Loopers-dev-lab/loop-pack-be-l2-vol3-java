package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Tag(name = "주문 API", description = "주문 생성 및 취소 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "내 주문 목록 조회", description = "회원의 주문 목록을 기간 필터와 페이징으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 목록 조회 성공")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> getOrders(
            @Parameter(description = "회원 DB PK") @RequestParam Long memberId,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable
    );

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 특정 주문을 조회합니다. 본인의 주문만 조회할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인의 주문이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> getOrder(
            @Parameter(description = "주문 ID (UUID)") @PathVariable String orderId,
            @Parameter(description = "회원 DB PK") @RequestParam Long memberId
    );

    @Operation(summary = "주문 생성", description = "여러 상품을 포함한 주문을 생성합니다. 대기열 활성화 시 X-Entry-Token 헤더가 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (빈 주문, 수량 < 1)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "대기열 토큰 없음 또는 유효하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(
            @Parameter(description = "대기열 입장 토큰") @RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
            @RequestBody OrderV1Dto.CreateOrderRequest request
    );

    @Operation(summary = "주문 취소", description = "PENDING 상태의 주문을 취소합니다. 이미 취소된 주문은 멱등 성공(200)으로 처리됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인의 주문이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelOrder(
            @Parameter(description = "주문 ID (UUID)") @PathVariable String orderId,
            @RequestBody OrderV1Dto.CancelOrderRequest request
    );
}

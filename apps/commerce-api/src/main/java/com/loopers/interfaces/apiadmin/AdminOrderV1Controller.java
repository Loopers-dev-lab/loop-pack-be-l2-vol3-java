package com.loopers.interfaces.apiadmin;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 전용 주문 REST API 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>기간별 주문 목록 조회 및 개별 주문 상세 조회 기능을 관리자에게 제공한다.
 * {@link OrderFacade}를 통해 조회한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1/orders")
@RequiredArgsConstructor
public class AdminOrderV1Controller {

    private final OrderFacade orderFacade;

    /**
     * 기간별 전체 주문 목록을 조회한다.
     *
     * <p>시작일과 종료일이 지정되지 않으면 기본적으로 최근 1개월 기간이 적용된다.</p>
     *
     * @param startAt 조회 시작일 (미지정 시 현재일 기준 1개월 전)
     * @param endAt   조회 종료일 (미지정 시 현재일)
     * @return 기간 내 주문 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminOrderV1Dto.AdminOrderResponse>>> list(
            @RequestParam(value = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(value = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt) {
        LocalDate now = LocalDate.now();
        LocalDateTime start = (startAt != null ? startAt : now.minusMonths(1)).atStartOfDay();
        LocalDateTime end = (endAt != null ? endAt : now).plusDays(1).atStartOfDay();

        List<AdminOrderV1Dto.AdminOrderResponse> response = orderFacade.getOrdersForAdmin(start, end).stream()
                .map(AdminOrderV1Dto.AdminOrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 개별 주문의 상세 정보를 조회한다.
     *
     * @param orderId 조회할 주문 ID
     * @return 주문 상세 정보 응답
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<AdminOrderV1Dto.AdminOrderResponse>> detail(
            @PathVariable Long orderId) {
        OrderInfo info = orderFacade.getOrderDetailForAdmin(orderId);
        return ResponseEntity.ok(ApiResponse.success(AdminOrderV1Dto.AdminOrderResponse.from(info)));
    }
}

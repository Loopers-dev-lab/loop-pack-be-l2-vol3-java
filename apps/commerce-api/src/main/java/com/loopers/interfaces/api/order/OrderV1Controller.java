package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderAppService;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>직접 주문 생성, 장바구니 주문 생성, 주문 목록 조회, 주문 상세 조회,
 * 주문 취소 기능을 제공한다. 복잡한 도메인으로 {@link OrderFacade}를 통해
 * 여러 서비스(주문, 재고, 장바구니 등)를 조합하여 처리한다.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final OrderAppService orderAppService;

    /**
     * 직접(DIRECT) 주문을 생성한다.
     *
     * <p>상품을 장바구니에 담지 않고 바로 주문한다. CAS 재고 예약이 수행된다.</p>
     *
     * @param loginId 로그인 ID (인증 헤더)
     * @param loginPw 비밀번호 (인증 헤더)
     * @param request 직접 주문 생성 요청 (주문 항목 목록)
     * @return 생성된 주문 상세 정보 (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> createDirectOrder(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody OrderV1Dto.CreateDirectOrderRequest request) {
        OrderInfo info = orderFacade.createDirectOrder(loginId, loginPw, request.toItems());
        return ResponseEntity.status(201).body(ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info)));
    }

    /**
     * 장바구니(CART) 주문을 생성한다.
     *
     * <p>장바구니에 담긴 상품들로 주문을 생성한다. CAS 재고 예약이 수행된다.</p>
     *
     * @param loginId 로그인 ID (인증 헤더)
     * @param loginPw 비밀번호 (인증 헤더)
     * @param request 장바구니 주문 생성 요청 (주문 항목 목록)
     * @return 생성된 주문 상세 정보 (HTTP 201)
     */
    @PostMapping("/cart")
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> createCartOrder(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody OrderV1Dto.CreateCartOrderRequest request) {
        OrderInfo info = orderFacade.createCartOrder(loginId, loginPw, request.toItems());
        return ResponseEntity.status(201).body(ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info)));
    }

    /**
     * 주문 목록을 조회한다.
     *
     * <p>조회 기간을 지정하지 않으면 최근 1개월 주문을 조회한다.</p>
     *
     * @param loginId 로그인 ID (인증 헤더)
     * @param loginPw 비밀번호 (인증 헤더)
     * @param startAt 조회 시작 날짜 (선택, 기본값: 1개월 전)
     * @param endAt 조회 종료 날짜 (선택, 기본값: 오늘)
     * @return 주문 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> getOrders(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestParam(value = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(value = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt) {
        LocalDate now = LocalDate.now();
        LocalDateTime start = (startAt != null ? startAt : now.minusMonths(1)).atStartOfDay();
        LocalDateTime end = (endAt != null ? endAt : now).plusDays(1).atStartOfDay();

        List<OrderInfo> orders = orderAppService.getOrders(loginId, loginPw, start, end);
        List<OrderV1Dto.OrderResponse> response = orders.stream()
                .map(OrderV1Dto.OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 주문 상세 정보를 조회한다.
     *
     * @param loginId 로그인 ID (인증 헤더)
     * @param loginPw 비밀번호 (인증 헤더)
     * @param orderId 조회할 주문 ID
     * @return 주문 상세 정보 응답 (주문 항목 포함)
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> getOrderDetail(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @PathVariable String orderId) {
        OrderInfo info = orderAppService.getOrderDetail(loginId, loginPw, orderId);
        return ResponseEntity.ok(ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info)));
    }

    /**
     * 주문을 취소한다.
     *
     * <p>PENDING_PAYMENT 상태의 주문만 취소 가능하다.
     * 취소 시 CAS 재고 해제 및 장바구니 복원(DIRECT 주문의 경우)이 수행된다.</p>
     *
     * @param loginId 로그인 ID (인증 헤더)
     * @param loginPw 비밀번호 (인증 헤더)
     * @param orderId 취소할 주문 ID
     * @return 성공 응답
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Object>> cancelOrder(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @PathVariable String orderId) {
        orderFacade.cancelOrder(loginId, loginPw, orderId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}

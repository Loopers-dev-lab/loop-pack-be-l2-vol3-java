package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<OrderV1Dto.CreateOrderResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(loginId, password, request.items(), request.userCouponId(), request.cardType(), request.cardNo(), request.updateDefaultCard());

        return ApiResponse.success(OrderV1Dto.CreateOrderResponse.from(info));
    }

    // 인증된 사용자의 주문 목록을 최신순 페이징으로 반환한다.
    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.PageResponse<OrderV1Dto.OrderSummaryResponse>> getOrderList(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(OrderV1Dto.PageResponse.from(
            orderFacade.getOrderList(loginId, password, page, size)
                .map(OrderV1Dto.OrderSummaryResponse::from)
        ));
    }

    // 주문 ID로 단건 상세(아이템 포함)를 반환한다. 타인의 주문은 404 처리.
    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getOrderDetail(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(
            orderFacade.getOrderDetail(loginId, password, orderId)
        ));
    }
}

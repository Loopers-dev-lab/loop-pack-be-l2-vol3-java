package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderSummaryInfo;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String HEADER_QUEUE_TOKEN = "X-Queue-Token";

    private final OrderFacade orderFacade;
    private final QueueService queueService;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> placeOrder(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @RequestHeader(HEADER_QUEUE_TOKEN) String queueToken,
        @Valid @RequestBody OrderV1Dto.PlaceOrderRequest request
    ) {
        queueService.validateTokenOrThrow(loginId, password, queueToken);
        OrderDetailInfo info = orderFacade.placeOrder(
            loginId,
            password,
            request.items().stream()
                .map(item -> new OrderFacade.PlaceOrderItem(item.productId(), item.quantity()))
                .toList(),
            request.couponId()
        );
        queueService.consumeToken(loginId, password, queueToken);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> getMyOrders(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        List<OrderSummaryInfo> orders = orderFacade.getMyOrders(loginId, password, startAt, endAt);
        List<OrderV1Dto.OrderSummaryResponse> response = orders.stream()
            .map(OrderV1Dto.OrderSummaryResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrder(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @PathVariable Long orderId
    ) {
        OrderDetailInfo info = orderFacade.getMyOrder(loginId, password, orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info));
    }
}

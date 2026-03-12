package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.member.MemberAuthService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final OrderService orderService;
    private final MemberAuthService memberAuthService;

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestBody OrderV1Dto.CreateRequest request,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberModel member = memberAuthService.authenticate(loginId, password);
        OrderResult result = orderFacade.placeOrder(member.getId(), request.toCommands());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(result));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> getMyOrders(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        MemberModel member = memberAuthService.authenticate(loginId, password);
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.of("Asia/Seoul"));
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul"));

        List<OrderModel> orders = orderService.getOrdersByUser(member.getId(), start, end);
        List<OrderV1Dto.OrderSummaryResponse> response = orders.stream()
            .map(OrderV1Dto.OrderSummaryResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @PathVariable Long orderId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberModel member = memberAuthService.authenticate(loginId, password);
        OrderModel order = orderService.getOrder(orderId, member.getId());
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order, items));
    }
}

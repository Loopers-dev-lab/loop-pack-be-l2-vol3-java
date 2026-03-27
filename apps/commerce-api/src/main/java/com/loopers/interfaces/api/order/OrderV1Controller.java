package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @LoginMember MemberModel member,
        @RequestBody OrderV1Dto.CreateRequest request
    ) {
        OrderResult result = orderFacade.placeOrder(member.getId(), request.toCommands(), request.couponIssueId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.fromResult(result));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> getMyOrders(
        @LoginMember MemberModel member,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.of("Asia/Seoul"));
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul"));
        List<OrderInfo> orders = orderFacade.getOrdersByUser(member.getId(), start, end);
        List<OrderV1Dto.OrderSummaryResponse> response = orders.stream()
            .map(OrderV1Dto.OrderSummaryResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @LoginMember MemberModel member,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(orderId, member.getId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}

package com.loopers.interfaces.api.order;

import com.loopers.application.order.queue.OrderQueueApplicationService;
import com.loopers.application.order.queue.OrderQueueStatusResult;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/order-queue")
@RequiredArgsConstructor
public class OrderQueueController {

    private final OrderQueueApplicationService orderQueueApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderQueueStatusResponse> enter(@AuthMember Member member) {
        final OrderQueueStatusResult result = orderQueueApplicationService.enter(member.id().value());
        return ApiResponse.success(OrderDto.OrderQueueStatusResponse.from(result));
    }

    @GetMapping("/me")
    public ApiResponse<OrderDto.OrderQueueStatusResponse> getStatus(@AuthMember Member member) {
        final OrderQueueStatusResult result = orderQueueApplicationService.getStatus(member.id().value());
        return ApiResponse.success(OrderDto.OrderQueueStatusResponse.from(result));
    }

    @GetMapping("/me/realtime")
    public ApiResponse<OrderDto.OrderQueueRealtimeStatusResponse> getRealtimeStatus(@AuthMember Member member) {
        return ApiResponse.success(
                OrderDto.OrderQueueRealtimeStatusResponse.from(
                        orderQueueApplicationService.getRealtimeStatus(member.id().value())
                )
        );
    }
}

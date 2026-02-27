package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.dto.CreateOrderApiReqDto;
import com.loopers.interfaces.api.order.dto.FindOrderApiResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<FindOrderApiResDto> createOrder(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                       @RequestHeader(HEADER_LOGIN_PW) String password,
                                                       @RequestBody CreateOrderApiReqDto request) {
        return ApiResponse.success(FindOrderApiResDto.from(orderFacade.createOrder(loginId, password, request.toDto())));
    }

    @GetMapping
    @Override
    public ApiResponse<List<FindOrderApiResDto>> findOrderList(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                               @RequestHeader(HEADER_LOGIN_PW) String password,
                                                               @RequestParam LocalDateTime startAt,
                                                               @RequestParam LocalDateTime endAt) {
        List<FindOrderApiResDto> result = orderFacade.getOrders(loginId, password, startAt, endAt).stream()
                .map(FindOrderApiResDto::from)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<FindOrderApiResDto> findOrder(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                      @RequestHeader(HEADER_LOGIN_PW) String password,
                                                      @PathVariable Long orderId) {
        return ApiResponse.success(FindOrderApiResDto.from(orderFacade.getOrder(loginId, password, orderId)));
    }
}

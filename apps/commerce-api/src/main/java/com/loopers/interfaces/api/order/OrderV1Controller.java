package com.loopers.interfaces.api.order;

import com.loopers.application.order.CreateOrderItemParam;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final OrderFacade orderFacade;
    private final UserFacade userFacade;

    public OrderV1Controller(OrderFacade orderFacade, UserFacade userFacade) {
        this.orderFacade = orderFacade;
        this.userFacade = userFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        List<CreateOrderItemParam> params = request.items().stream()
            .map(item -> new CreateOrderItemParam(
                item.productId(),
                item.quantity(),
                item.optionId()
            ))
            .toList();
        var info = orderFacade.placeOrder(userId, entryToken, params, request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @PathVariable Long orderId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        OrderV1Dto.OrderResponse response = orderFacade.findById(userId, orderId)
            .map(OrderV1Dto.OrderResponse::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        return ApiResponse.success(response);
    }

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + loginId));
        if (start == null || end == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "시작일과 종료일을 지정해야 합니다.");
        }
        if (end.isBefore(start)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "종료일은 시작일 이후여야 합니다.");
        }
        int safePage = Math.max(DEFAULT_PAGE, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, 100);
        List<OrderV1Dto.OrderResponse> list = orderFacade.findOrders(userId, start, end, safePage, safeSize)
            .stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(list);
    }

    @PostMapping("/{orderId}/cancel")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @PathVariable Long orderId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = orderFacade.cancel(userId, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}

package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderController {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String LDAP_ADMIN = "loopers.admin";

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> listOrders(
            @RequestHeader(value = HEADER_LDAP, required = false) String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAdmin(ldap);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderApplicationService.listAll(pageable);
        return ApiResponse.success(OrderDto.OrderListResponse.from(orders));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
            @RequestHeader(value = HEADER_LDAP, required = false) String ldap,
            @PathVariable Long orderId
    ) {
        validateAdmin(ldap);
        Order order = orderApplicationService.getById(orderId, null, true);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
            @RequestHeader(value = HEADER_LDAP, required = false) String ldap,
            @PathVariable Long orderId
    ) {
        validateAdmin(ldap);
        Order order = orderApplicationService.cancel(orderId, null, true);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    private void validateAdmin(String ldap) {
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 헤더가 없습니다.");
        }
        if (!LDAP_ADMIN.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN, "관리자 권한이 없습니다.");
        }
    }
}

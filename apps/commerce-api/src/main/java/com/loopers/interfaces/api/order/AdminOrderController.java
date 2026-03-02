package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 주문 관리 어드민 API 컨트롤러 (X-Loopers-Ldap 인증 필요) */
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderController implements AdminOrderApiSpec {

    private final OrderAdminFacade orderAdminFacade;

    public AdminOrderController(OrderAdminFacade orderAdminFacade) {
        this.orderAdminFacade = orderAdminFacade;
    }

    /** 전체 주문 목록 페이지네이션 조회 */
    @GetMapping
    @Override
    public ApiResponse<AdminOrderResponse.OrderListResponse> getOrders(
            @AuthAdmin String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OrderAdminFacade.OrderAdminListResult result = orderAdminFacade.getOrders(page, size);

        List<AdminOrderResponse.OrderSummary> summaries = result.orders().stream()
                .map(o -> new AdminOrderResponse.OrderSummary(
                        o.orderId(), o.orderNumber(), o.userId(),
                        o.status(), o.totalAmount(), o.createdAt()))
                .toList();

        return ApiResponse.success(new AdminOrderResponse.OrderListResponse(
                summaries, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    /** 주문 상세 조회 */
    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<AdminOrderResponse.OrderDetail> getOrder(
            @AuthAdmin String ldap,
            @PathVariable Long orderId
    ) {
        OrderAdminFacade.OrderAdminDetailResult result = orderAdminFacade.getOrderDetail(orderId);

        List<AdminOrderResponse.OrderItemDetail> items = result.items().stream()
                .map(item -> new AdminOrderResponse.OrderItemDetail(
                        item.productName(), item.brandName(),
                        item.unitPrice(), item.quantity(), item.lineTotal()))
                .toList();

        return ApiResponse.success(new AdminOrderResponse.OrderDetail(
                result.orderId(), result.orderNumber(), result.userId(), result.status(),
                result.ordererName(), result.ordererPhone(),
                result.receiverName(), result.receiverPhone(),
                result.zipCode(), result.addressLine1(), result.addressLine2(),
                result.subtotalAmount(), result.discountAmount(),
                result.pointUsedAmount(), result.shippingFee(), result.totalAmount(),
                items, result.createdAt()));
    }
}

package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class PaymentController implements PaymentApiSpec {

    private final PaymentFacade paymentFacade;

    public PaymentController(PaymentFacade paymentFacade) {
        this.paymentFacade = paymentFacade;
    }

    @PutMapping("/{orderId}/discount")
    @Override
    public ApiResponse<PaymentResponse.DiscountAppliedResponse> applyDiscount(
            @AuthUser User user,
            @PathVariable Long orderId,
            @RequestBody PaymentRequest.ApplyDiscountRequest request) {
        Order order = paymentFacade.applyDiscount(
                orderId, user.getId(), request.issuedCouponId(), request.pointAmount());

        return ApiResponse.success(new PaymentResponse.DiscountAppliedResponse(
                order.getId(), order.getSubtotalAmount(), order.getDiscountAmount(),
                order.getPointUsedAmount(), order.getShippingFee(), order.getTotalAmount()));
    }

    @PostMapping("/{orderId}/pay")
    @Override
    public ApiResponse<PaymentResponse.PaymentResult> pay(
            @AuthUser User user,
            @PathVariable Long orderId,
            @RequestBody PaymentRequest.PayRequest request) {
        Payment payment = paymentFacade.requestPayment(
                orderId, user.getId(), request.paymentMethod(), request.issuedCouponId());

        return ApiResponse.success(new PaymentResponse.PaymentResult(
                payment.getId(), payment.getOrderId(), payment.getStatus().name(),
                payment.getPaymentMethod(), payment.getRequestedAmount(),
                payment.getApprovedAmount(), payment.getPgTxnId(), payment.getApprovedAt()));
    }
}

package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderDomainService.OrderLineRequest;
import com.loopers.domain.order.OrderLine;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderDomainService orderDomainService;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public OrderResult placeOrder(Long memberId, List<OrderLineRequest> items, Long couponId) {
        IssuedCoupon issuedCoupon = null;
        CouponTemplate couponTemplate = null;

        if (couponId != null) {
            issuedCoupon = issuedCouponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

            issuedCoupon.validateOwnership(memberId);

            if (!issuedCoupon.isUsable()) {
                throw new CoreException(ErrorType.COUPON_UNAVAILABLE);
            }

            couponTemplate = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

            if (couponTemplate.isExpired()) {
                throw new CoreException(ErrorType.COUPON_EXPIRED);
            }
        }

        List<OrderLine> orderLines = orderDomainService.prepareOrderLines(items);

        long originalAmount = orderLines.stream().mapToLong(OrderLine::getTotalPrice).sum();

        Order order;
        if (issuedCoupon != null) {
            couponTemplate.validateMinOrderAmount(originalAmount);
            long discountAmount = couponTemplate.calculateDiscount(originalAmount);
            order = orderDomainService.createOrderWithCoupon(memberId, orderLines, couponId, discountAmount);
            issuedCoupon.use();
        } else {
            order = orderDomainService.createOrder(memberId, orderLines);
        }

        List<OrderLineInfo> resultLines = order.getOrderLines().stream()
            .map(ol -> new OrderLineInfo(ol.getProductId(), ol.getQuantity(), ol.getUnitPrice()))
            .collect(Collectors.toList());
        return new OrderResult(
            order.getId(), order.getStatus(),
            order.getOriginalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
            resultLines
        );
    }

    public record OrderResult(Long orderId, String status, long originalAmount, long discountAmount, long totalAmount, List<OrderLineInfo> orderLines) {}

    public record OrderLineInfo(Long productId, int quantity, long unitPrice) {}
}

package com.loopers.application.order;

import com.loopers.application.order.dto.CreateOrderReqDto;
import com.loopers.application.order.dto.FindOrderResDto;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final OrderProductService orderProductService;
    private final MemberService memberService;
    private final ProductService productService;
    private final CouponService couponService;

    @Transactional(rollbackFor = {Exception.class})
    public FindOrderResDto createOrder(String loginId, String password, CreateOrderReqDto dto) {
        Member member = memberService.findMember(loginId, password);

        List<OrderProduct> orderProducts = productService.decreaseStockAndCreateOrderProducts(dto.toOrderItems());

        int subtotal = orderProducts.stream().mapToInt(OrderProduct::subtotal).sum();
        int discountAmount = calculateDiscount(dto.userCouponId(), member.getId(), subtotal);

        OrderCommand.Create command = new OrderCommand.Create(member.getId(), orderProducts, discountAmount, dto.userCouponId());
        return FindOrderResDto.from(orderService.createOrder(command));
    }

    public List<FindOrderResDto> getOrders(String loginId, String password, LocalDateTime startAt, LocalDateTime endAt) {
        Member member = memberService.findMember(loginId, password);
        OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(member.getId(), startAt, endAt);
        return orderService.getOrders(command).stream()
                .map(this::populateAndConvert)
                .toList();
    }

    public FindOrderResDto getOrder(String loginId, String password, Long orderId) {
        Member member = memberService.findMember(loginId, password);
        OrderCommand.GetByMember command = new OrderCommand.GetByMember(member.getId(), orderId);
        Orders orders = orderService.getOrder(command);
        return populateAndConvert(orders);
    }

    private FindOrderResDto populateAndConvert(Orders orders) {
        List<OrderProduct> orderProducts = orderProductService.findByOrderId(orders.getId());
        Orders populated = Orders.reconstruct(
                orders.getId(), orders.getOrderNumber(), orders.getMemberId(),
                orders.getTotalPrice().value(), orders.getDiscountAmount().value(),
                orders.getUserCouponId(), orders.getStatus(), orderProducts
        );
        return FindOrderResDto.from(populated);
    }

    private int calculateDiscount(Long userCouponId, Long memberId, int subtotal) {
        if (userCouponId == null) {
            return 0;
        }
        CouponTemplate template = couponService.useUserCoupon(userCouponId, memberId, subtotal);
        return template.calculateDiscount(subtotal);
    }
}

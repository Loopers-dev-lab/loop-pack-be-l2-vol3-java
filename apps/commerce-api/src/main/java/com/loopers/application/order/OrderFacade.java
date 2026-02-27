package com.loopers.application.order;

import com.loopers.application.order.dto.CreateOrderReqDto;
import com.loopers.application.order.dto.FindOrderResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final OrderProductService orderProductService;
    private final MemberService memberService;
    private final ProductService productService;

    @Transactional(rollbackFor = {Exception.class})
    public FindOrderResDto createOrder(String loginId, String password, CreateOrderReqDto dto) {
        Member member = memberService.findMember(loginId, password);

        List<Long> productIds = dto.items().stream()
                .map(CreateOrderReqDto.OrderItemReqDto::productId)
                .toList();
        List<Product> products = productService.getProductsByIds(productIds);

        Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderProduct> orderProducts = dto.items().stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    productService.decreaseStock(product, item.quantity());
                    return OrderProduct.create(
                            product.getId(),
                            product.getName().value(),
                            product.getPrice().value(),
                            item.quantity()
                    );
                })
                .toList();

        OrderCommand.Create command = new OrderCommand.Create(member.getId(), orderProducts);
        Orders savedOrder = orderService.createOrder(command);

        List<OrderProduct> savedProducts = orderProductService.saveAll(savedOrder.getId(), orderProducts);
        Orders result = Orders.reconstruct(savedOrder.getId(), savedOrder.getMemberId(), savedOrder.getTotalPrice().value(), savedProducts);
        return FindOrderResDto.from(result);
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
        Orders populated = Orders.reconstruct(orders.getId(), orders.getMemberId(), orders.getTotalPrice().value(), orderProducts);
        return FindOrderResDto.from(populated);
    }
}

package com.loopers.application.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;

    /**
     * 주문 생성 (US-O01)
     * 상품 존재 확인 + 재고 검증 → 주문 생성(스냅샷) → 재고 차감
     */
    @Transactional
    public OrderInfo create(Long userId, OrderCreateCommand command) {
        Map<Long, Quantity> quantityByProductId = command.items().stream()
                .collect(Collectors.toMap(
                        OrderCreateCommand.Item::productId,
                        item -> new Quantity(item.quantity())
                ));

        // 비관적 락으로 재고 확인 + 차감 원자적 수행 (BR-O03, BR-O04)
        // SELECT FOR UPDATE → 재고 검증 → decreaseStock (dirty checking) 순서로 TOCTOU 방지
        List<Product> products = productService.verifyAndDecreaseStock(quantityByProductId);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 브랜드명 일괄 조회 (스냅샷용)
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        // OrderItem 스냅샷 구성 (BR-O05)
        List<OrderItem> orderItems = command.items().stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    String brandName = brandNameMap.get(product.getBrandId());
                    return new OrderItem(
                            product.getId(),
                            new Quantity(item.quantity()),
                            product.getName(),
                            brandName,
                            product.getPrice()
                    );
                })
                .toList();

        // 주문 저장
        Order order = orderService.create(userId, orderItems);

        return OrderInfo.of(order);
    }

    /**
     * 회원 주문 상세 조회 (US-O03)
     * 존재 확인 → 소유권 확인 (BR-O06)
     */
    @Transactional(readOnly = true)
    public OrderInfo findById(Long orderId, Long userId) {
        Order order = orderService.findById(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        return OrderInfo.of(order);
    }

    /**
     * 회원 주문 목록 조회 (US-O02)
     * 기간 필터 + 소유권 제한 (BR-O06, BR-O08)
     */
    @Transactional(readOnly = true)
    public List<OrderInfo> findAllByUserId(Long userId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime from = startAt.atStartOfDay(KST);
        ZonedDateTime to = endAt.plusDays(1).atStartOfDay(KST);
        return orderService.findAllByUserId(userId, from, to).stream()
                .map(OrderInfo::of)
                .toList();
    }
}

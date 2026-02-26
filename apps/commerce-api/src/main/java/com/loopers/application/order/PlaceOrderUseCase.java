package com.loopers.application.order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 주문을 생성합니다.
 *
 * <p>주문 상품의 재고를 확인하고 차감한 뒤 주문을 생성합니다.
 * 재고 확인부터 주문 생성까지 하나의 트랜잭션으로 처리됩니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class PlaceOrderUseCase {

    private final OrderService orderService;
    private final ProductService productService;

    /**
     * @param command 주문 생성 커맨드 (사용자 ID, 주문 항목 목록)
     * @return 생성된 주문 ID
     */
    @Transactional
    public Long execute(PlaceOrderCommand command) {
        List<Long> productIds = command.getProductIds();
        Map<Long, Product> products = productService.getProductsByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
        command.items().stream()
                .sorted(Comparator.comparing(PlaceOrderCommand.OrderItemCommand::productId))
                .forEach(item -> productService.deductStock(item.productId(), item.quantity()));
        Order order = orderService.create(command.toCart(products));
        return order.getId();
    }
}

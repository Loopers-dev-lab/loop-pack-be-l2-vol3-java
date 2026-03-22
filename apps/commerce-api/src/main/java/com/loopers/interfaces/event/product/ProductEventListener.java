package com.loopers.interfaces.event.product;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductEvent;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 상품 도메인의 이벤트 리스너.
 *
 * <p>상품 관련 이벤트를 수신하여 상품 도메인의 후속 처리를 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final LikeService likeService;
    private final OrderService orderService;
    private final ProductService productService;

    /**
     * 상품 삭제 이벤트를 처리한다.
     *
     * <p>해당 상품의 좋아요를 일괄 삭제한다.</p>
     *
     * @param event 상품 삭제 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(ProductEvent.ProductDeleted event) {
        likeService.deleteLikesByProductId(event.productId());
    }

    /**
     * 주문 실패 이벤트를 처리한다.
     *
     * <p>주문 항목별로 차감된 재고를 복원한다.</p>
     *
     * @param event 주문 실패 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(OrderEvent.OrderFailed event) {
        Order order = orderService.getById(event.orderId());
        order.getOrderItems().forEach(item ->
                productService.restoreStock(item.getProductId(), item.getQuantity())
        );
    }
}

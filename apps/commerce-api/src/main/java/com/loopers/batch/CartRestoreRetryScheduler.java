package com.loopers.batch;

import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 매일 05:30 -- DIRECT 주문 중 취소/만료 상태이고 장바구니 복원이 안 된 건을 재시도.
 * order_cart_restore 레코드가 없는 건만 대상.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartRestoreRetryScheduler {

    private final OrderService orderService;
    private final CartService cartService;

    @Scheduled(cron = "0 30 5 * * *")
    @Transactional
    public void retryFailedCartRestores() {
        List<OrderModel> unrestoredOrders = orderService.findUnrestoredDirectOrders();

        if (unrestoredOrders.isEmpty()) {
            log.info("[장바구니복원재시도] 미복원 건 없음");
            return;
        }

        log.warn("[장바구니복원재시도] {}건 감지", unrestoredOrders.size());
        int restored = 0;
        for (OrderModel order : unrestoredOrders) {
            try {
                if (!orderService.existsCartRestore(order.getOrderId())) {
                    List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
                    orderService.saveCartRestore(
                        OrderCartRestoreModel.create(order.getOrderId(), order.getUserId(),
                            RestoreReason.USER_CANCELLED, RestoreTriggerSource.MANUAL));
                    List<CartService.RestoreItem> restoreItems = items.stream()
                        .map(item -> new CartService.RestoreItem(item.getProductId(), item.getQuantity()))
                        .toList();
                    cartService.restoreFromOrder(order.getUserId(), restoreItems);
                    restored++;
                    log.info("[장바구니복원재시도] orderId={} 복원 성공", order.getOrderId());
                }
            } catch (Exception e) {
                log.error("[장바구니복원재시도실패] orderId={}", order.getOrderId(), e);
            }
        }
        log.info("[장바구니복원재시도] 완료: {}건 복원 / {}건 감지", restored, unrestoredOrders.size());
    }
}

package com.loopers.application.stock;

import com.loopers.application.order.OrderService;
import com.loopers.application.payment.PaymentService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciler {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final TransactionTemplate transactionTemplate;

    public void reconcileLeakedReservations() {
        Set<Long> reservedProductIds = stockService.findProductIdsWithReservedStock();
        if (reservedProductIds.isEmpty()) return;

        List<Order> targets = orderService.findOrdersByStatusWithItems(OrderStatus.CANCELED)
                .stream()
                .filter(order -> hasReservedProduct(order, reservedProductIds))
                .toList();
        if (targets.isEmpty()) return;

        log.info("점유 누수 보정 대상 주문 {}건 탐지", targets.size());

        for (Order order : targets) {
            releaseLeakedReservation(order, reservedProductIds);
        }
    }

    public void reconcileMissingConfirmations() {
        Set<Long> reservedProductIds = stockService.findProductIdsWithReservedStock();
        if (reservedProductIds.isEmpty()) return;

        List<Order> targets = orderService.findOrdersByStatusWithItems(OrderStatus.PAID)
                .stream()
                .filter(order -> hasReservedProduct(order, reservedProductIds))
                .filter(order -> paymentService.existsSucceededPayment(order.getId()))
                .toList();
        if (targets.isEmpty()) return;

        log.info("확정 누락 보정 대상 주문 {}건 탐지", targets.size());

        for (Order order : targets) {
            confirmMissingOrder(order, reservedProductIds);
        }
    }

    private void releaseLeakedReservation(Order order, Set<Long> reservedProductIds) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Map<Long, Integer> quantities = filterReservedQuantities(order, reservedProductIds);
                stockService.releaseReserved(quantities);
            });
            log.info("주문 {} 점유 누수 보정 완료", order.getId());
        } catch (Exception e) {
            log.error("주문 {} 점유 누수 보정 실패", order.getId(), e);
        }
    }

    private void confirmMissingOrder(Order order, Set<Long> reservedProductIds) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Map<Long, Integer> quantities = filterReservedQuantities(order, reservedProductIds);
                stockService.confirm(quantities);
            });
            log.info("주문 {} 확정 누락 보정 완료", order.getId());
        } catch (Exception e) {
            log.error("주문 {} 확정 누락 보정 실패", order.getId(), e);
        }
    }

    private boolean hasReservedProduct(Order order, Set<Long> reservedProductIds) {
        return order.getOrderItems().stream()
                .anyMatch(item -> reservedProductIds.contains(item.getProductId()));
    }

    private Map<Long, Integer> filterReservedQuantities(Order order, Set<Long> reservedProductIds) {
        return order.getOrderItems().stream()
                .filter(item -> reservedProductIds.contains(item.getProductId()))
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
                ));
    }
}

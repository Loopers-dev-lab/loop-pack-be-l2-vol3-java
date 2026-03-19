package com.loopers.application.stock;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${stock.reconciliation.interval-ms:300000}")
    public void reconcile() {
        reconcileLeakedReservations();
        reconcileMissingConfirmations();
    }

    private void reconcileLeakedReservations() {
        Set<Long> reservedProductIds = stockRepository.findProductIdsWithReservedStock();
        if (reservedProductIds.isEmpty()) return;

        List<Order> canceledOrders = orderRepository.findAllByStatusWithItems(OrderStatus.CANCELED);
        List<Order> targets = canceledOrders.stream()
                .filter(order -> hasReservedProduct(order, reservedProductIds))
                .toList();
        if (targets.isEmpty()) return;

        log.info("점유 누수 보정 대상 주문 {}건 탐지", targets.size());

        for (Order order : targets) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Map<Long, Integer> productQuantities = toProductQuantities(order, reservedProductIds);
                    stockService.releaseReserved(productQuantities);
                });
                log.info("주문 {} 점유 누수 보정 완료", order.getId());
            } catch (Exception e) {
                log.error("주문 {} 점유 누수 보정 실패", order.getId(), e);
            }
        }
    }

    private void reconcileMissingConfirmations() {
        Set<Long> reservedProductIds = stockRepository.findProductIdsWithReservedStock();
        if (reservedProductIds.isEmpty()) return;

        List<Order> paidOrders = orderRepository.findAllByStatusWithItems(OrderStatus.PAID);
        List<Order> targets = paidOrders.stream()
                .filter(order -> hasReservedProduct(order, reservedProductIds))
                .filter(order -> paymentRepository.existsSucceededByOrderId(order.getId()))
                .toList();
        if (targets.isEmpty()) return;

        log.info("확정 누락 보정 대상 주문 {}건 탐지", targets.size());

        for (Order order : targets) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Map<Long, Integer> productQuantities = toProductQuantities(order, reservedProductIds);
                    stockService.confirm(productQuantities);
                });
                log.info("주문 {} 확정 누락 보정 완료", order.getId());
            } catch (Exception e) {
                log.error("주문 {} 확정 누락 보정 실패", order.getId(), e);
            }
        }
    }

    private boolean hasReservedProduct(Order order, Set<Long> reservedProductIds) {
        return order.getOrderItems().stream()
                .anyMatch(item -> reservedProductIds.contains(item.getProductId()));
    }

    private Map<Long, Integer> toProductQuantities(Order order, Set<Long> reservedProductIds) {
        return order.getOrderItems().stream()
                .filter(item -> reservedProductIds.contains(item.getProductId()))
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
                ));
    }
}

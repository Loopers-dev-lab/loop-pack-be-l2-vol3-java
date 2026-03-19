package com.loopers.application.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockScheduler {

    private final StockReconciler stockReconciler;

    @Scheduled(fixedDelayString = "${stock.reconciliation.leaked-reservation.interval-ms:300000}")
    public void reconcileLeakedReservations() {
        stockReconciler.reconcileLeakedReservations();
    }

    @Scheduled(fixedDelayString = "${stock.reconciliation.missing-confirmation.interval-ms:300000}")
    public void reconcileMissingConfirmations() {
        stockReconciler.reconcileMissingConfirmations();
    }
}

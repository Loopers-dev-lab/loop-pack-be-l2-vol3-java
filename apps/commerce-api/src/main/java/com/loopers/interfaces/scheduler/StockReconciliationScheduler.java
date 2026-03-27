package com.loopers.interfaces.scheduler;

import com.loopers.application.stock.StockReconciler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockReconciliationScheduler {

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

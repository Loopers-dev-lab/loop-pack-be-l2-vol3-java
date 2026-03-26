package com.loopers.batch;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 만료 스케줄러.
 * 60초 간격으로 결제 대기 시간이 초과된 주문을 자동 만료 처리한다.
 * 개별 주문 만료 실패 시에도 나머지 주문의 처리를 계속한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderFacade orderFacade;
    private final OrderService orderService;
    private final PaymentService paymentService;

    /**
     * 결제 대기 시간이 초과된 주문을 조회하여 만료 처리한다.
     * 개별 주문 처리 실패 시 경고 로그를 남기고 다음 주문으로 진행한다.
     */
    @Scheduled(fixedDelay = 60000)
    public void expireOrders() {
        List<Long> expiredOrderIds = orderService.findExpiredPendingOrderIds();
        if (expiredOrderIds.isEmpty()) {
            return;
        }
        // 결제 진행 중(REQUESTED) 주문은 만료 대상에서 제외
        List<Long> safeToExpire = expiredOrderIds.stream()
                .filter(orderId -> !paymentService.hasActivePayment(orderId))
                .toList();
        if (safeToExpire.isEmpty()) {
            log.info("만료 대상 주문 {}건 중 결제 진행 중 제외 → 처리 대상 없음", expiredOrderIds.size());
            return;
        }
        log.info("만료 대상 주문 {}건 처리 시작 (결제 진행 중 {}건 제외)",
                safeToExpire.size(), expiredOrderIds.size() - safeToExpire.size());
        int successCount = 0;
        for (Long orderId : safeToExpire) {
            try {
                orderFacade.expireOrder(orderId);
                successCount++;
            } catch (Exception e) {
                log.warn("주문 만료 처리 실패: orderId={}", orderId, e);
            }
        }
        log.info("만료 처리 완료: 성공 {}/전체 {}", successCount, safeToExpire.size());
    }
}

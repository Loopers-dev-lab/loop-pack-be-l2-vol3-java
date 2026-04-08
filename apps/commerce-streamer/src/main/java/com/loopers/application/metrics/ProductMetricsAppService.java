package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMetricsAppService {
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductDailyMetricsRepository productDailyMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handleLikeToggled(String eventId, Long productId, boolean liked, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        ensureExists(productId);
        int affected = liked
                ? productMetricsRepository.incrementLikeCount(productId, occurredAt)
                : productMetricsRepository.decrementLikeCount(productId, occurredAt);

        if (affected == 0) {
            log.info("오래된 이벤트 무시: eventId={}, productId={}", eventId, productId);
        } else {
            int delta = liked ? 1 : -1;
            productDailyMetricsRepository.upsertLikeCount(productId, occurredAt.toLocalDate(), delta, occurredAt);
            log.info("좋아요 메트릭 갱신: productId={}, liked={}", productId, liked);
        }
    }

    @Transactional
    public void handleProductViewed(String eventId, Long productId, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        ensureExists(productId);
        int affected = productMetricsRepository.incrementViewCount(productId, occurredAt);
        if (affected == 0) {
            log.info("오래된 이벤트 무시: eventId={}, productId={}", eventId, productId);
        } else {
            productDailyMetricsRepository.upsertViewCount(productId, occurredAt.toLocalDate(), occurredAt);
            log.info("조회 메트릭 갱신: productId={}", productId);
        }
    }

    @Transactional
    public void handleOrderCreated(String eventId, List<Long> productIds, long totalAmount, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        long amountPerProduct = productIds.isEmpty() ? 0 : totalAmount / productIds.size();
        for (Long productId : productIds) {
            ensureExists(productId);
            productMetricsRepository.incrementSalesCount(productId, occurredAt);
            productDailyMetricsRepository.upsertOrderAmount(
                    productId, occurredAt.toLocalDate(), Math.max(amountPerProduct, 1), occurredAt);
        }
        log.info("판매 메트릭 갱신: productIds={}", productIds);
    }

    @Transactional
    public void handleOrderCanceled(String eventId, List<Long> productIds, long totalAmount, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        long amountPerProduct = productIds.isEmpty() ? 0 : totalAmount / productIds.size();
        for (Long productId : productIds) {
            ensureExists(productId);
            productMetricsRepository.decrementSalesCount(productId, occurredAt);
            productDailyMetricsRepository.upsertOrderAmount(
                    productId, occurredAt.toLocalDate(), -Math.max(amountPerProduct, 1), occurredAt);
        }
        log.info("판매 메트릭 차감: productIds={}", productIds);
    }

    private void ensureExists(Long productId) {
        productMetricsRepository.insertIgnore(productId);
    }
}

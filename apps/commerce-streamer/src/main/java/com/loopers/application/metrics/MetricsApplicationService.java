package com.loopers.application.metrics;

import com.loopers.domain.event.OrderItemPayload;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
@Service
public class MetricsApplicationService {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_RETRY = 3;

    public void incrementLikeCount(String eventId, Long productId) {
        executeWithRetry(eventId, productId, ProductMetrics::incrementLikeCount);
    }

    public void decrementLikeCount(String eventId, Long productId) {
        executeWithRetry(eventId, productId, ProductMetrics::decrementLikeCount);
    }

    public void incrementViewCount(String eventId, Long productId) {
        executeWithRetry(eventId, productId, ProductMetrics::incrementViewCount);
    }

    public void incrementSaleCount(String eventId, List<OrderItemPayload> items) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                txTemplate.executeWithoutResult(status -> {
                    for (OrderItemPayload item : items) {
                        ProductMetrics metrics = getOrCreate(item.productId());
                        metrics.incrementSaleCount(item.quantity());
                        productMetricsRepository.save(metrics);
                    }
                    eventHandledRepository.save(new EventHandled(eventId));
                });
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    log.error("[Metrics] OptimisticLock 재시도 초과. eventId={}", eventId);
                    throw e;
                }
                log.debug("[Metrics] OptimisticLock 충돌, 재시도 {}/{}. eventId={}",
                    attempt + 1, MAX_RETRY, eventId);
            }
        }
    }

    public void decrementSaleCount(String eventId, List<OrderItemPayload> items) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                txTemplate.executeWithoutResult(status -> {
                    for (OrderItemPayload item : items) {
                        ProductMetrics metrics = getOrCreate(item.productId());
                        metrics.decrementSaleCount(item.quantity());
                        productMetricsRepository.save(metrics);
                    }
                    eventHandledRepository.save(new EventHandled(eventId));
                });
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    log.error("[Metrics] OptimisticLock 재시도 초과. eventId={}", eventId);
                    throw e;
                }
                log.debug("[Metrics] OptimisticLock 충돌, 재시도 {}/{}. eventId={}",
                    attempt + 1, MAX_RETRY, eventId);
            }
        }
    }

    private void executeWithRetry(String eventId, Long productId, Consumer<ProductMetrics> operation) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                txTemplate.executeWithoutResult(status -> {
                    ProductMetrics metrics = getOrCreate(productId);
                    operation.accept(metrics);
                    productMetricsRepository.save(metrics);
                    eventHandledRepository.save(new EventHandled(eventId));
                });
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    log.error("[Metrics] OptimisticLock 재시도 초과. productId={}", productId);
                    throw e;
                }
                log.debug("[Metrics] OptimisticLock 충돌, 재시도 {}/{}. productId={}",
                    attempt + 1, MAX_RETRY, productId);
            }
        }
    }

    private ProductMetrics getOrCreate(Long productId) {
        return productMetricsRepository.findByProductId(productId)
            .orElseGet(() -> {
                try {
                    return productMetricsRepository.save(new ProductMetrics(productId));
                } catch (DataIntegrityViolationException e) {
                    return productMetricsRepository.findByProductId(productId)
                        .orElseThrow(() -> e);
                }
            });
    }
}

package com.loopers.application.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 상품 상세 조회 응답 경로에서 Outbox 쓰기를 분리한다. 실패해도 조회 결과에 영향을 주지 않는다.
 */
@Component
public class ProductViewOutboxAsyncPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductViewOutboxAsyncPublisher.class);

    private final ProductViewOutboxRecorder recorder;

    public ProductViewOutboxAsyncPublisher(ProductViewOutboxRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder);
    }

    @Async("productViewOutboxExecutor")
    public void scheduleRecordProductViewed(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            recorder.recordProductViewed(productId);
        } catch (Exception e) {
            log.warn("상품 조회 Outbox 기록 실패 productId={}", productId, e);
        }
    }
}

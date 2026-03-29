package com.loopers.domain.product;

public record ProductViewedEvent(
    String userId,    // 비로그인 시 "unknown"
    Long productId,
    String userAgent  // 원본 저장, 분석은 외부 시스템에서
) {}
package com.loopers.domain.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 운영 통계 도메인 프로젝션 DTO 모음.
 * 도메인/인프라 레이어에서 사용되며, application 레이어의 {@link com.loopers.application.stats.StatsInfo}로 변환된다.
 */
public class StatsProjection {

    /**
     * 주문 현황 개요 프로젝션.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Overview {
        private final long pendingCount;
        private final long cancelledCount;
        private final long expiredCount;
    }

    /**
     * 일별 주문 통계 프로젝션.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DailyOrderStat {
        private final LocalDate date;
        private final long orderCount;
        private final BigDecimal totalAmount;
    }

    /**
     * 상품 통계 프로젝션.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProductStat {
        private final String productId;
        private final String productName;
        private final long count;
    }

    /**
     * 저재고 상품 프로젝션.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class LowStockProduct {
        private final String productId;
        private final String productName;
        private final int onHand;
        private final int reserved;
        private final int availableQty;
    }
}

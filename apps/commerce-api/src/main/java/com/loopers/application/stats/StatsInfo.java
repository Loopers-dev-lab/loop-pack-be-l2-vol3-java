package com.loopers.application.stats;

import com.loopers.domain.stats.StatsProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 운영 통계 정보 DTO 모음.
 * 주문 현황 개요, 일별 주문 통계, 인기 상품, 저재고 상품 정보를 포함한다.
 */
public class StatsInfo {

    /**
     * 주문 현황 개요 DTO.
     * 결제 대기, 취소, 만료 건수를 포함한다.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Overview {
        private final long pendingCount;
        private final long cancelledCount;
        private final long expiredCount;

        public static Overview from(StatsProjection.Overview projection) {
            return Overview.builder()
                    .pendingCount(projection.getPendingCount())
                    .cancelledCount(projection.getCancelledCount())
                    .expiredCount(projection.getExpiredCount())
                    .build();
        }
    }

    /**
     * 일별 주문 통계 DTO.
     * 특정 날짜의 주문 건수와 총 주문 금액을 포함한다.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DailyOrderStat {
        private final LocalDate date;
        private final long orderCount;
        private final BigDecimal totalAmount;

        public static DailyOrderStat from(StatsProjection.DailyOrderStat projection) {
            return DailyOrderStat.builder()
                    .date(projection.getDate())
                    .orderCount(projection.getOrderCount())
                    .totalAmount(projection.getTotalAmount())
                    .build();
        }
    }

    /**
     * 상품 통계 DTO.
     * 인기 좋아요 상품, 인기 주문 상품 등 상품별 집계 결과를 표현한다.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProductStat {
        private final String productId;
        private final String productName;
        private final long count;

        public static ProductStat from(StatsProjection.ProductStat projection) {
            return ProductStat.builder()
                    .productId(projection.getProductId())
                    .productName(projection.getProductName())
                    .count(projection.getCount())
                    .build();
        }
    }

    /**
     * 저재고 상품 DTO.
     * 가용 재고가 임계값 이하인 상품의 재고 현황을 포함한다.
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

        public static LowStockProduct from(StatsProjection.LowStockProduct projection) {
            return LowStockProduct.builder()
                    .productId(projection.getProductId())
                    .productName(projection.getProductName())
                    .onHand(projection.getOnHand())
                    .reserved(projection.getReserved())
                    .availableQty(projection.getAvailableQty())
                    .build();
        }
    }
}

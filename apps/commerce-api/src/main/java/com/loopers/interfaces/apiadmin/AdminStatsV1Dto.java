package com.loopers.interfaces.apiadmin;

import com.loopers.application.stats.StatsInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관리자 운영 통계 API의 요청/응답 DTO를 정의하는 클래스.
 */
public class AdminStatsV1Dto {

    /**
     * 주문 현황 개요 응답 DTO.
     *
     * <p>결제 대기, 취소, 만료 건수를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OverviewResponse {
        private long pendingCount;
        private long cancelledCount;
        private long expiredCount;

        /**
         * {@link StatsInfo.Overview}를 주문 현황 개요 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param overview 주문 현황 개요 정보
         * @return 변환된 주문 현황 개요 응답 DTO
         */
        public static OverviewResponse from(StatsInfo.Overview overview) {
            return OverviewResponse.builder()
                    .pendingCount(overview.getPendingCount())
                    .cancelledCount(overview.getCancelledCount())
                    .expiredCount(overview.getExpiredCount())
                    .build();
        }
    }

    /**
     * 일별 주문 통계 응답 DTO.
     *
     * <p>날짜별 주문 건수와 총 금액을 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class DailyOrderStatResponse {
        private LocalDate date;
        private long orderCount;
        private BigDecimal totalAmount;

        /**
         * {@link StatsInfo.DailyOrderStat}을 일별 주문 통계 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param stat 일별 주문 통계 정보
         * @return 변환된 일별 주문 통계 응답 DTO
         */
        public static DailyOrderStatResponse from(StatsInfo.DailyOrderStat stat) {
            return DailyOrderStatResponse.builder()
                    .date(stat.getDate())
                    .orderCount(stat.getOrderCount())
                    .totalAmount(stat.getTotalAmount())
                    .build();
        }
    }

    /**
     * 상품 통계 응답 DTO.
     *
     * <p>좋아요 수 또는 주문 수 기준의 인기 상품 통계 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class ProductStatResponse {
        private String productId;
        private String productName;
        private long count;

        /**
         * {@link StatsInfo.ProductStat}을 상품 통계 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param stat 상품 통계 정보
         * @return 변환된 상품 통계 응답 DTO
         */
        public static ProductStatResponse from(StatsInfo.ProductStat stat) {
            return ProductStatResponse.builder()
                    .productId(stat.getProductId())
                    .productName(stat.getProductName())
                    .count(stat.getCount())
                    .build();
        }
    }

    /**
     * 저재고 상품 응답 DTO.
     *
     * <p>보유 수량(onHand), 예약 수량(reserved), 가용 수량(availableQty)을 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class LowStockProductResponse {
        private String productId;
        private String productName;
        private int onHand;
        private int reserved;
        private int availableQty;

        /**
         * {@link StatsInfo.LowStockProduct}를 저재고 상품 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param stock 저재고 상품 정보
         * @return 변환된 저재고 상품 응답 DTO
         */
        public static LowStockProductResponse from(StatsInfo.LowStockProduct stock) {
            return LowStockProductResponse.builder()
                    .productId(stock.getProductId())
                    .productName(stock.getProductName())
                    .onHand(stock.getOnHand())
                    .reserved(stock.getReserved())
                    .availableQty(stock.getAvailableQty())
                    .build();
        }
    }
}

package com.loopers.interfaces.apiadmin;

import com.loopers.domain.stats.StatsProjection;
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
         * {@link StatsProjection.Overview}를 주문 현황 개요 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param projection 주문 현황 개요 프로젝션
         * @return 변환된 주문 현황 개요 응답 DTO
         */
        public static OverviewResponse from(StatsProjection.Overview projection) {
            return OverviewResponse.builder()
                    .pendingCount(projection.getPendingCount())
                    .cancelledCount(projection.getCancelledCount())
                    .expiredCount(projection.getExpiredCount())
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
         * {@link StatsProjection.DailyOrderStat}을 일별 주문 통계 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param projection 일별 주문 통계 프로젝션
         * @return 변환된 일별 주문 통계 응답 DTO
         */
        public static DailyOrderStatResponse from(StatsProjection.DailyOrderStat projection) {
            return DailyOrderStatResponse.builder()
                    .date(projection.getDate())
                    .orderCount(projection.getOrderCount())
                    .totalAmount(projection.getTotalAmount())
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
        private Long productId;
        private String productName;
        private long count;

        /**
         * {@link StatsProjection.ProductStat}을 상품 통계 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param projection 상품 통계 프로젝션
         * @return 변환된 상품 통계 응답 DTO
         */
        public static ProductStatResponse from(StatsProjection.ProductStat projection) {
            return ProductStatResponse.builder()
                    .productId(projection.getProductId())
                    .productName(projection.getProductName())
                    .count(projection.getCount())
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
        private Long productId;
        private String productName;
        private int onHand;
        private int reserved;
        private int availableQty;

        /**
         * {@link StatsProjection.LowStockProduct}를 저재고 상품 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param projection 저재고 상품 프로젝션
         * @return 변환된 저재고 상품 응답 DTO
         */
        public static LowStockProductResponse from(StatsProjection.LowStockProduct projection) {
            return LowStockProductResponse.builder()
                    .productId(projection.getProductId())
                    .productName(projection.getProductName())
                    .onHand(projection.getOnHand())
                    .reserved(projection.getReserved())
                    .availableQty(projection.getAvailableQty())
                    .build();
        }
    }
}

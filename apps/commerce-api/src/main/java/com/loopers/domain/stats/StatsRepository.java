package com.loopers.domain.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * 운영 통계 리포지토리 인터페이스.
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며, 인프라스트럭처 계층에서 구현한다.
 */
public interface StatsRepository {

    /**
     * 기간별 주문 현황 개요를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 결제 대기·취소·만료 건수를 포함하는 주문 현황 개요
     */
    StatsProjection.Overview getOverview(LocalDate startAt, LocalDate endAt);

    /**
     * 기간별 일별 주문 통계를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 일별 주문 건수 및 총 금액 목록
     */
    List<StatsProjection.DailyOrderStat> getDailyOrderStats(LocalDate startAt, LocalDate endAt);

    /**
     * 좋아요 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 좋아요 수 내림차순 상품 목록
     */
    List<StatsProjection.ProductStat> getTopLikedProducts(int limit);

    /**
     * 주문 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 주문 수 내림차순 상품 목록
     */
    List<StatsProjection.ProductStat> getTopOrderedProducts(int limit);

    /**
     * 가용 재고가 임계값 이하인 저재고 상품 목록을 조회한다.
     *
     * @param threshold 재고 임계값
     * @return 저재고 상품 목록
     */
    List<StatsProjection.LowStockProduct> getLowStockProducts(int threshold);
}

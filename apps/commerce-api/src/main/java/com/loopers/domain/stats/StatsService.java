package com.loopers.domain.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 운영 통계 도메인 서비스.
 * 주문 현황 개요, 일별 주문 통계, 인기 상품, 저재고 상품 조회를 담당한다.
 * 관리자 대시보드에서 활용되는 통계 데이터를 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;

    /**
     * 기간별 주문 현황 개요를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 결제 대기·취소·만료 건수를 포함하는 주문 현황 개요
     */
    public StatsProjection.Overview getOverview(LocalDate startAt, LocalDate endAt) {
        return statsRepository.getOverview(startAt, endAt);
    }

    /**
     * 기간별 일별 주문 통계를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 일별 주문 건수 및 총 금액 목록
     */
    public List<StatsProjection.DailyOrderStat> getDailyOrderStats(LocalDate startAt, LocalDate endAt) {
        return statsRepository.getDailyOrderStats(startAt, endAt);
    }

    /**
     * 좋아요 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 좋아요 수 내림차순 상품 목록
     */
    public List<StatsProjection.ProductStat> getTopLikedProducts(int limit) {
        return statsRepository.getTopLikedProducts(limit);
    }

    /**
     * 주문 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 주문 수 내림차순 상품 목록
     */
    public List<StatsProjection.ProductStat> getTopOrderedProducts(int limit) {
        return statsRepository.getTopOrderedProducts(limit);
    }

    /**
     * 가용 재고가 임계값 이하인 저재고 상품 목록을 조회한다.
     *
     * @param threshold 재고 임계값
     * @return 저재고 상품 목록
     */
    public List<StatsProjection.LowStockProduct> getLowStockProducts(int threshold) {
        return statsRepository.getLowStockProducts(threshold);
    }
}

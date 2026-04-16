package com.loopers.batch.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 주간 상품 랭킹 저장소 인터페이스.
 */
public interface ProductRankWeeklyRepository {

    /**
     * 주간 랭킹 엔트리를 저장합니다.
     */
    ProductRankWeekly save(ProductRankWeekly productRankWeekly);

    /**
     * 주간 랭킹 엔트리를 일괄 저장합니다.
     */
    List<ProductRankWeekly> saveAll(List<ProductRankWeekly> productRankWeeklyList);

    /**
     * 특정 주간의 랭킹을 조회합니다.
     *
     * @param periodStartDate 주간 시작일 (월요일)
     * @return 해당 주간의 TOP 100 랭킹 목록 (순위순 정렬)
     */
    List<ProductRankWeekly> findByPeriodStartDate(LocalDate periodStartDate);

    /**
     * 특정 상품의 주간 랭킹 이력을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 해당 상품의 주간 랭킹 이력 (최신순 정렬)
     */
    List<ProductRankWeekly> findByProductIdOrderByPeriodStartDateDesc(Long productId);

    /**
     * 특정 주간의 특정 상품 랭킹을 조회합니다.
     */
    Optional<ProductRankWeekly> findByPeriodStartDateAndProductId(LocalDate periodStartDate, Long productId);

    /**
     * 특정 주간의 랭킹 데이터를 삭제합니다.
     * (재집계 시 기존 데이터 삭제용)
     */
    void deleteByPeriodStartDate(LocalDate periodStartDate);

    /**
     * 특정 주간의 랭킹 데이터 존재 여부를 확인합니다.
     */
    boolean existsByPeriodStartDate(LocalDate periodStartDate);
}
package com.loopers.batch.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 월간 상품 랭킹 저장소 인터페이스.
 */
public interface ProductRankMonthlyRepository {

    /**
     * 월간 랭킹 엔트리를 저장합니다.
     */
    ProductRankMonthly save(ProductRankMonthly productRankMonthly);

    /**
     * 월간 랭킹 엔트리를 일괄 저장합니다.
     */
    List<ProductRankMonthly> saveAll(List<ProductRankMonthly> productRankMonthlyList);

    /**
     * 특정 월간의 랭킹을 조회합니다.
     *
     * @param periodStartDate 월간 시작일 (매월 1일)
     * @return 해당 월간의 TOP 100 랭킹 목록 (순위순 정렬)
     */
    List<ProductRankMonthly> findByPeriodStartDate(LocalDate periodStartDate);

    /**
     * 특정 상품의 월간 랭킹 이력을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 해당 상품의 월간 랭킹 이력 (최신순 정렬)
     */
    List<ProductRankMonthly> findByProductIdOrderByPeriodStartDateDesc(Long productId);

    /**
     * 특정 월간의 특정 상품 랭킹을 조회합니다.
     */
    Optional<ProductRankMonthly> findByPeriodStartDateAndProductId(LocalDate periodStartDate, Long productId);

    /**
     * 특정 월간의 랭킹 데이터를 삭제합니다.
     * (재집계 시 기존 데이터 삭제용)
     */
    void deleteByPeriodStartDate(LocalDate periodStartDate);

    /**
     * 특정 월간의 랭킹 데이터 존재 여부를 확인합니다.
     */
    boolean existsByPeriodStartDate(LocalDate periodStartDate);
}
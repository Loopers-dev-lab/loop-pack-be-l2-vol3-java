package com.loopers.infrastructure.persistence.jpa.rank;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 월간 상품 랭킹 JPA Repository.
 */
public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyJpaEntity, Long> {

    /**
     * 특정 월간의 랭킹을 순위순으로 조회합니다.
     */
    List<ProductRankMonthlyJpaEntity> findByPeriodStartDateOrderByRankNumberAsc(LocalDate periodStartDate);

    /**
     * 특정 상품의 월간 랭킹 이력을 최신순으로 조회합니다.
     */
    List<ProductRankMonthlyJpaEntity> findByProductIdOrderByPeriodStartDateDesc(Long productId);

    /**
     * 특정 월간의 특정 상품 랭킹을 조회합니다.
     */
    Optional<ProductRankMonthlyJpaEntity> findByPeriodStartDateAndProductId(LocalDate periodStartDate, Long productId);

    /**
     * 특정 월간의 랭킹 데이터를 삭제합니다.
     */
    @Modifying
    @Query("DELETE FROM ProductRankMonthlyJpaEntity e WHERE e.periodStartDate = :periodStartDate")
    void deleteByPeriodStartDate(@Param("periodStartDate") LocalDate periodStartDate);

    /**
     * 특정 월간의 랭킹 데이터 존재 여부를 확인합니다.
     */
    boolean existsByPeriodStartDate(LocalDate periodStartDate);
}
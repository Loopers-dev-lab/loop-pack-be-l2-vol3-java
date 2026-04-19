package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * mv_product_rank_monthly Spring Data JPA 레포지토리.
 *
 * MvProductRankWeeklyJpaRepository 와 구조가 동일하며, 대상 엔티티만 다르다.
 * MonthlyRankingRepositoryImpl 에서만 사용하며 도메인 레이어에 직접 노출하지 않는다 (DIP).
 */
public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, MvProductRankId> {

    /**
     * 지정 기준일의 월간 랭킹을 rank 오름차순(1위부터)으로 페이지 조회한다.
     *
     * @param baseDate 집계 기준일 (MV base_date 컬럼)
     * @param pageable 페이지 정보 (PageRequest.of(page-1, size))
     */
    List<MvProductRankMonthly> findByBaseDateOrderByRankAsc(LocalDate baseDate, Pageable pageable);

    /**
     * 지정 기준일의 전체 랭킹 엔트리 수를 반환한다.
     * API 페이지네이션의 totalElements 산정에 사용한다.
     */
    long countByBaseDate(LocalDate baseDate);
}

package com.loopers.infrastructure.persistence.jpa.rank;

import com.loopers.batch.domain.ProductRankWeekly;
import com.loopers.batch.domain.ProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 주간 상품 랭킹 저장소 구현체.
 */
@Repository
@RequiredArgsConstructor
public class ProductRankWeeklyRepositoryImpl implements ProductRankWeeklyRepository {

    private final ProductRankWeeklyJpaRepository jpaRepository;
    private final ProductRankWeeklyMapper mapper;

    @Override
    public ProductRankWeekly save(ProductRankWeekly productRankWeekly) {
        ProductRankWeeklyJpaEntity entity = mapper.toJpaEntity(productRankWeekly);
        ProductRankWeeklyJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<ProductRankWeekly> saveAll(List<ProductRankWeekly> productRankWeeklyList) {
        List<ProductRankWeeklyJpaEntity> entities = productRankWeeklyList.stream()
            .map(mapper::toJpaEntity)
            .toList();
        List<ProductRankWeeklyJpaEntity> savedEntities = jpaRepository.saveAll(entities);
        return savedEntities.stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ProductRankWeekly> findByPeriodStartDate(LocalDate periodStartDate) {
        return jpaRepository.findByPeriodStartDateOrderByRankNumberAsc(periodStartDate).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ProductRankWeekly> findByProductIdOrderByPeriodStartDateDesc(Long productId) {
        return jpaRepository.findByProductIdOrderByPeriodStartDateDesc(productId).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<ProductRankWeekly> findByPeriodStartDateAndProductId(LocalDate periodStartDate, Long productId) {
        return jpaRepository.findByPeriodStartDateAndProductId(periodStartDate, productId)
            .map(mapper::toDomain);
    }

    @Override
    public void deleteByPeriodStartDate(LocalDate periodStartDate) {
        jpaRepository.deleteByPeriodStartDate(periodStartDate);
    }

    @Override
    public boolean existsByPeriodStartDate(LocalDate periodStartDate) {
        return jpaRepository.existsByPeriodStartDate(periodStartDate);
    }
}
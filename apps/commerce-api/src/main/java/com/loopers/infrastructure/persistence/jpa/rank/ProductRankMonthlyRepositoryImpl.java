package com.loopers.infrastructure.persistence.jpa.rank;

import com.loopers.batch.domain.ProductRankMonthly;
import com.loopers.batch.domain.ProductRankMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 월간 상품 랭킹 저장소 구현체.
 */
@Repository
@RequiredArgsConstructor
public class ProductRankMonthlyRepositoryImpl implements ProductRankMonthlyRepository {

    private final ProductRankMonthlyJpaRepository jpaRepository;
    private final ProductRankMonthlyMapper mapper;

    @Override
    public ProductRankMonthly save(ProductRankMonthly productRankMonthly) {
        ProductRankMonthlyJpaEntity entity = mapper.toJpaEntity(productRankMonthly);
        ProductRankMonthlyJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<ProductRankMonthly> saveAll(List<ProductRankMonthly> productRankMonthlyList) {
        List<ProductRankMonthlyJpaEntity> entities = productRankMonthlyList.stream()
            .map(mapper::toJpaEntity)
            .toList();
        List<ProductRankMonthlyJpaEntity> savedEntities = jpaRepository.saveAll(entities);
        return savedEntities.stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ProductRankMonthly> findByPeriodStartDate(LocalDate periodStartDate) {
        return jpaRepository.findByPeriodStartDateOrderByRankNumberAsc(periodStartDate).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ProductRankMonthly> findByProductIdOrderByPeriodStartDateDesc(Long productId) {
        return jpaRepository.findByProductIdOrderByPeriodStartDateDesc(productId).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<ProductRankMonthly> findByPeriodStartDateAndProductId(LocalDate periodStartDate, Long productId) {
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
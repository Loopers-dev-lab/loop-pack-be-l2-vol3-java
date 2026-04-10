package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductDailySignalModel;
import com.loopers.domain.ranking.ProductDailySignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductDailySignalRepositoryImpl implements ProductDailySignalRepository {

    private final ProductDailySignalJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsertViewCount(Long productDbId, LocalDate date, long delta) {
        jpaRepository.upsertViewCount(productDbId, date, delta);
    }

    @Override
    @Transactional
    public void upsertLikeCount(Long productDbId, LocalDate date, long delta) {
        jpaRepository.upsertLikeCount(productDbId, date, delta);
    }

    @Override
    @Transactional
    public void upsertOrderAmount(Long productDbId, LocalDate date, BigDecimal amount) {
        jpaRepository.upsertOrderAmount(productDbId, date, amount);
    }

    @Override
    public List<ProductDailySignalModel> findBySignalDate(LocalDate date) {
        return jpaRepository.findBySignalDate(date);
    }
}

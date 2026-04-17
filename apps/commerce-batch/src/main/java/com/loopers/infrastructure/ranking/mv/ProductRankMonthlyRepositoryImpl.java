package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMonthlyRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRankMonthlyRepositoryImpl implements ProductRankMonthlyRepository {

    private final MvProductRankMonthlyJpaRepository jpaRepository;

    public ProductRankMonthlyRepositoryImpl(MvProductRankMonthlyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(ProductRankMvRow row) {
        jpaRepository.save(toEntity(row));
    }

    @Override
    public List<ProductRankMvRow> findByPeriodKeyOrderByRankAsc(String periodKey) {
        return jpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey).stream()
                .map(ProductRankMonthlyRepositoryImpl::toRow)
                .toList();
    }

    private static MvProductRankMonthlyEntity toEntity(ProductRankMvRow row) {
        MvProductRankMonthlyEntity entity = new MvProductRankMonthlyEntity();
        entity.setPeriodKey(row.periodKey());
        entity.setProductId(row.productId());
        entity.setRankValue(row.rank());
        entity.setScore(row.score());
        entity.setVersion(row.version());
        entity.setUpdatedAt(row.updatedAt());
        return entity;
    }

    private static ProductRankMvRow toRow(MvProductRankMonthlyEntity entity) {
        return new ProductRankMvRow(
                Optional.ofNullable(entity.getId()),
                entity.getPeriodKey(),
                entity.getProductId(),
                entity.getRankValue(),
                entity.getScore(),
                entity.getVersion(),
                entity.getUpdatedAt()
        );
    }
}

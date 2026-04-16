package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMvRow;
import com.loopers.domain.ranking.mv.ProductRankWeeklyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRankWeeklyRepositoryImpl implements ProductRankWeeklyRepository {

    private final MvProductRankWeeklyJpaRepository jpaRepository;

    public ProductRankWeeklyRepositoryImpl(MvProductRankWeeklyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(ProductRankMvRow row) {
        jpaRepository.save(toEntity(row));
    }

    @Override
    public List<ProductRankMvRow> findByPeriodKeyOrderByRankAsc(String periodKey) {
        return jpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey).stream()
                .map(ProductRankWeeklyRepositoryImpl::toRow)
                .toList();
    }

    private static MvProductRankWeeklyEntity toEntity(ProductRankMvRow row) {
        MvProductRankWeeklyEntity entity = new MvProductRankWeeklyEntity();
        entity.setPeriodKey(row.periodKey());
        entity.setProductId(row.productId());
        entity.setRankValue(row.rank());
        entity.setScore(row.score());
        entity.setVersion(row.version());
        entity.setUpdatedAt(row.updatedAt());
        return entity;
    }

    private static ProductRankMvRow toRow(MvProductRankWeeklyEntity entity) {
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

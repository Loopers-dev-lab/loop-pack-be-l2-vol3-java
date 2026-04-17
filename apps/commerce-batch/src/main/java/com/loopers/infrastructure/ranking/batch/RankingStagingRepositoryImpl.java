package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RankingStagingRepositoryImpl implements RankingStagingRepository {

    private final MvProductRankStagingJpaRepository jpaRepository;

    public RankingStagingRepositoryImpl(MvProductRankStagingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void deleteByPeriodTypeAndPeriodKey(String periodType, String periodKey) {
        jpaRepository.deleteByPeriodTypeAndPeriodKey(periodType, periodKey);
    }

    @Override
    @Transactional
    public void saveRankedRows(String periodType, String periodKey, List<RankingStagingRankRow> rows) {
        Instant now = Instant.now();
        List<MvProductRankStagingEntity> entities = new ArrayList<>(rows.size());
        for (RankingStagingRankRow row : rows) {
            MvProductRankStagingEntity e = new MvProductRankStagingEntity();
            e.setPeriodType(periodType);
            e.setPeriodKey(periodKey);
            e.setProductId(row.productId());
            e.setRankValue(row.rank());
            e.setScore(row.score());
            e.setVersion(0);
            e.setUpdatedAt(now);
            entities.add(e);
        }
        jpaRepository.saveAll(entities);
    }
}

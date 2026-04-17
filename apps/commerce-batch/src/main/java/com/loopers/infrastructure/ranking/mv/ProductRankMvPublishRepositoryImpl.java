package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMvPublishRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public class ProductRankMvPublishRepositoryImpl implements ProductRankMvPublishRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    public ProductRankMvPublishRepositoryImpl(
            MvProductRankWeeklyJpaRepository weeklyJpaRepository,
            MvProductRankMonthlyJpaRepository monthlyJpaRepository
    ) {
        this.weeklyJpaRepository = weeklyJpaRepository;
        this.monthlyJpaRepository = monthlyJpaRepository;
    }

    /**
     * 주간 랭킹 MV를 대체한다.
     *
     * @param periodKey 기간 키
     * @param rows 랭킹 행
     * @param publishedAt 발행 시간
     */
    @Override
    @Transactional
    public void replaceWeeklyPeriod(String periodKey, List<ProductRankMvRow> rows, Instant publishedAt) {
        weeklyJpaRepository.deleteByPeriodKey(periodKey);
        entityManager.flush();
        entityManager.clear();
        weeklyJpaRepository.saveAll(rows.stream().map(r -> toWeeklyEntity(r, publishedAt)).toList());
    }

    /**
     * 월간 랭킹 MV를 대체한다.
     *
     * @param periodKey 기간 키
     * @param rows 랭킹 행
     * @param publishedAt 발행 시간
     */
    @Override
    @Transactional
    public void replaceMonthlyPeriod(String periodKey, List<ProductRankMvRow> rows, Instant publishedAt) {
        monthlyJpaRepository.deleteByPeriodKey(periodKey);
        entityManager.flush();
        entityManager.clear();
        monthlyJpaRepository.saveAll(rows.stream().map(r -> toMonthlyEntity(r, publishedAt)).toList());
    }

    /**
     * 주간 랭킹 MV 엔티티를 생성한다.
     *
     * @param row 랭킹 행
     * @param publishedAt 발행 시간
     * @return 주간 랭킹 MV 엔티티
     */
    private static MvProductRankWeeklyEntity toWeeklyEntity(ProductRankMvRow row, Instant publishedAt) {
        MvProductRankWeeklyEntity e = new MvProductRankWeeklyEntity();
        e.setPeriodKey(row.periodKey());
        e.setProductId(row.productId());
        e.setRankValue(row.rank());
        e.setScore(row.score());
        e.setVersion(row.version());
        e.setUpdatedAt(publishedAt);
        return e;
    }

    /**
     * 월간 랭킹 MV 엔티티를 생성한다.
     *
     * @param row 랭킹 행
     * @param publishedAt 발행 시간
     * @return 월간 랭킹 MV 엔티티
     */
    private static MvProductRankMonthlyEntity toMonthlyEntity(ProductRankMvRow row, Instant publishedAt) {
        MvProductRankMonthlyEntity e = new MvProductRankMonthlyEntity();
        e.setPeriodKey(row.periodKey());
        e.setProductId(row.productId());
        e.setRankValue(row.rank());
        e.setScore(row.score());
        e.setVersion(row.version());
        e.setUpdatedAt(publishedAt);
        return e;
    }
}

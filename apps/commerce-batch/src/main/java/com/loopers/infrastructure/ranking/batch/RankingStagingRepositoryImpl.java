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

    /**
     * 스테이징 MV를 삭제한다.
     *
     * @param periodType 기간 타입
     * @param periodKey 기간 키
     */
    @Override
    @Transactional
    public void deleteByPeriodTypeAndPeriodKey(String periodType, String periodKey) {
        jpaRepository.deleteByPeriodTypeAndPeriodKey(periodType, periodKey);
    }

    /**
     * 스테이징 MV를 저장한다.
     *
     * @param periodType 기간 타입
     * @param periodKey 기간 키
     * @param rows 랭킹 스테이징 행
     */
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

    /**
     * 스테이징 MV를 조회한다.
     *
     * @param periodType 기간 타입
     * @param periodKey 기간 키
     * @return 랭킹 스테이징 행
     */
    @Override
    @Transactional(readOnly = true)
    public List<RankingStagingRankRow> findRankedRows(String periodType, String periodKey) {
        return jpaRepository.findByPeriodTypeAndPeriodKeyOrderByRankValueAsc(periodType, periodKey).stream()
                .map(e -> new RankingStagingRankRow(e.getRankValue(), e.getProductId(), e.getScore()))
                .toList();
    }
}

package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.ranking.RankingSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link RankingSnapshotRepository}의 JPA 구현체.
 *
 * <p>MySQL의 {@code ON DUPLICATE KEY UPDATE}를 활용하여
 * 동일한 (productId, scoreDate) 조합이 존재하면 score를 갱신하고,
 * 존재하지 않으면 새로 생성한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class RankingSnapshotRepositoryImpl implements RankingSnapshotRepository {

    private final RankingSnapshotJpaRepository rankingSnapshotJpaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>각 상품에 대해 개별 upsert 쿼리를 실행한다.
     * 빈 Map이 전달되면 아무 작업도 수행하지 않는다.</p>
     */
    @Override
    @Transactional
    public void saveAll(LocalDate scoreDate, Map<Long, Double> productScores) {
        productScores.forEach((productId, score) ->
                rankingSnapshotJpaRepository.upsert(productId, scoreDate, score)
        );
    }
}

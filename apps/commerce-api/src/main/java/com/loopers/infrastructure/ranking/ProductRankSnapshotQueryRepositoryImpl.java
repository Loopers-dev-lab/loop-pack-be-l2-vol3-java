package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.ProductRankSnapshotQueryRepository;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.ranking.ProductRankSnapshotJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class ProductRankSnapshotQueryRepositoryImpl implements ProductRankSnapshotQueryRepository {

    private final EntityManager entityManager;

    @Override
    public List<ProductRankSnapshot> findRankings(RankingType rankingType, LocalDate rankDate, int offset, int size) {
        return entityManager.createQuery(
                        "SELECT p FROM ProductRankSnapshot p " +
                        "WHERE p.rankingType = :rankingType AND p.rankDate = :rankDate " +
                        "ORDER BY p.rankPosition ASC", ProductRankSnapshot.class)
                .setParameter("rankingType", rankingType)
                .setParameter("rankDate", rankDate)
                .setFirstResult(offset)
                .setMaxResults(size)
                .getResultList();
    }

    @Override
    public Optional<LocalDate> findLatestRankDate(RankingType rankingType) {
        List<LocalDate> result = entityManager.createQuery(
                        "SELECT MAX(p.rankDate) FROM ProductRankSnapshot p " +
                        "WHERE p.rankingType = :rankingType", LocalDate.class)
                .setParameter("rankingType", rankingType)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.ofNullable(result.get(0));
    }
}

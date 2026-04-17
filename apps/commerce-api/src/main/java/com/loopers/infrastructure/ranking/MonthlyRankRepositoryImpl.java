package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyRank;
import com.loopers.domain.ranking.MonthlyRankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MonthlyRankRepositoryImpl implements MonthlyRankRepository {

    private final MonthlyRankJpaRepository jpa;

    @Override
    public Optional<LocalDate> findLatestSnapshotDate() {
        return jpa.findLatestSnapshotDate();
    }

    @Override
    public Page<MonthlyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable) {
        return jpa.findBySnapshotDateOrderByRankAsc(snapshotDate, pageable);
    }
}

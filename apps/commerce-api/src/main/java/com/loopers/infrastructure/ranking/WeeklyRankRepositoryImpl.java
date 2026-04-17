package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.WeeklyRank;
import com.loopers.domain.ranking.WeeklyRankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WeeklyRankRepositoryImpl implements WeeklyRankRepository {

    private final WeeklyRankJpaRepository jpa;

    @Override
    public Optional<LocalDate> findLatestSnapshotDate() {
        return jpa.findLatestSnapshotDate();
    }

    @Override
    public Page<WeeklyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable) {
        return jpa.findBySnapshotDateOrderByRankAsc(snapshotDate, pageable);
    }
}

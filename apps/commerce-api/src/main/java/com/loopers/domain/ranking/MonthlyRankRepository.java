package com.loopers.domain.ranking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface MonthlyRankRepository {
    Optional<LocalDate> findLatestSnapshotDate();
    Page<MonthlyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable);
}

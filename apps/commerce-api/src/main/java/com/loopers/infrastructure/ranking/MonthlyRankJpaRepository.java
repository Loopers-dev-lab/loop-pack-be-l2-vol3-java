package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyRank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface MonthlyRankJpaRepository extends JpaRepository<MonthlyRank, Long> {

    @Query("SELECT MAX(m.snapshotDate) FROM MonthlyRank m")
    Optional<LocalDate> findLatestSnapshotDate();

    Page<MonthlyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable);
}

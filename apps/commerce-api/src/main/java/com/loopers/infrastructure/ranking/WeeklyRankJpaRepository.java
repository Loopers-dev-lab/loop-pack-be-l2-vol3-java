package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.WeeklyRank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyRankJpaRepository extends JpaRepository<WeeklyRank, Long> {

    @Query("SELECT MAX(w.snapshotDate) FROM WeeklyRank w")
    Optional<LocalDate> findLatestSnapshotDate();

    Page<WeeklyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable);
}

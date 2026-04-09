package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<RankingEntry> findTopN(LocalDate date, long offset, long size);

    List<RankingEntry> findByCursor(LocalDate date, Double cursorScore, long size);

    Optional<Long> findRank(LocalDate date, Long productDbId);

    Optional<Double> findScore(LocalDate date, Long productDbId);

    long countMembers(LocalDate date);

    List<RankingEntry> findHourlyTopN(LocalDate date, int hour, long offset, long size);

    List<RankingEntry> findHourlyCursor(LocalDate date, int hour, Double cursorScore, long size);

    Optional<Long> findHourlyRank(LocalDate date, int hour, Long productDbId);

    long countHourlyMembers(LocalDate date, int hour);
}

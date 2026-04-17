package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class RankingService {

    private final RealtimeRankingRepository realtimeRankingRepository;

    public List<RankingEntry> findDailyRanking(LocalDate date, long offset, long size) {
        return realtimeRankingRepository.findDailyRanking(date, offset, size);
    }

    public long countDailyRanking(LocalDate date) {
        return realtimeRankingRepository.countDailyRanking(date);
    }

    public Long findProductRank(LocalDate date, Long productId) {
        return realtimeRankingRepository.findProductDailyRank(date, productId);
    }

    public List<RankingEntry> findHourlyRanking(LocalDate date, int hour, long offset, long size) {
        return realtimeRankingRepository.findHourlyRanking(date, hour, offset, size);
    }

    public long countHourlyRanking(LocalDate date, int hour) {
        return realtimeRankingRepository.countHourlyRanking(date, hour);
    }
}

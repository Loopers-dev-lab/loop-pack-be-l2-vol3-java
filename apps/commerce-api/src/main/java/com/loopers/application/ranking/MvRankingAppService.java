package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductRankMvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MvRankingAppService {

    private final ProductRankMvRepository productRankMvRepository;

    @Transactional(readOnly = true)
    public List<RankingEntry> getWeeklyRankings(String yearWeek, int page, int size) {
        return productRankMvRepository.findWeeklyRankings(yearWeek, page, size).stream()
                .map(mv -> new RankingEntry(mv.getRanking(), mv.getProductId(), mv.getScore()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RankingEntry> getMonthlyRankings(String yearMonth, int page, int size) {
        return productRankMvRepository.findMonthlyRankings(yearMonth, page, size).stream()
                .map(mv -> new RankingEntry(mv.getRanking(), mv.getProductId(), mv.getScore()))
                .toList();
    }
}

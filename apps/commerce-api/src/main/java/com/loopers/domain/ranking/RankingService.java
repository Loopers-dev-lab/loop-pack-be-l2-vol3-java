package com.loopers.domain.ranking;

import java.util.List;

import com.loopers.domain.shared.annotation.DomainService;

import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;

    public List<RankingItem> readTopRanked(String date, int offset, int count) {
        String key = RankingKeyResolver.resolve(date);
        return rankingRepository.readTopRanked(key, offset, count);
    }
}

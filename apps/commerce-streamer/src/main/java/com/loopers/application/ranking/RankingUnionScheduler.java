package com.loopers.application.ranking;

import com.loopers.infrastructure.ranking.RankingRedisUpdater;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingUnionScheduler {

    private final RankingRedisUpdater rankingRedisUpdater;

    @Scheduled(fixedRate = 5000)
    public void unionStoreRanking() {
        rankingRedisUpdater.unionStoreRanking(LocalDate.now());
    }
}

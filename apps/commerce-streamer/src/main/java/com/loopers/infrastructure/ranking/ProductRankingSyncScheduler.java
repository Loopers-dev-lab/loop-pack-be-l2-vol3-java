package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.ProductRankingSyncApplicationService;
import com.loopers.application.ranking.RankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.ranking.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductRankingSyncScheduler {

    private final ProductRankingSyncApplicationService productRankingSyncApplicationService;
    private final RankingProperties rankingProperties;

    @Scheduled(fixedDelayString = "${loopers.ranking.sync.fixed-delay-ms:60000}")
    public void sync() {
        productRankingSyncApplicationService.syncCurrentDailyRanking();
        productRankingSyncApplicationService.syncCurrentHourlyRanking();
    }

    @Scheduled(cron = "${loopers.ranking.carry-over.daily-cron:0 50 23 * * *}")
    public void carryOverTomorrowRanking() {
        if (!rankingProperties.carryOver().enabled()) {
            return;
        }
        productRankingSyncApplicationService.prepareTomorrowDailyRanking();
    }

    @Scheduled(cron = "0 50 * * * *")
    public void carryOverNextHourlyRanking() {
        if (!rankingProperties.carryOver().enabled()) {
            return;
        }
        productRankingSyncApplicationService.prepareNextHourlyRanking();
    }
}

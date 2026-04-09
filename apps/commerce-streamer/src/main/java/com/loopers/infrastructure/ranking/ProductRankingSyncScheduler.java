package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.ProductRankingSyncApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.ranking.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductRankingSyncScheduler {

    private final ProductRankingSyncApplicationService productRankingSyncApplicationService;

    @Scheduled(fixedDelayString = "${loopers.ranking.sync.fixed-delay-ms:60000}")
    public void sync() {
        productRankingSyncApplicationService.syncTodayRanking();
    }
}

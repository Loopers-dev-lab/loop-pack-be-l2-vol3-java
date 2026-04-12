package com.loopers.interfaces.scheduler;

import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class RankingCarryOverScheduler {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;
    private final double carryWeight;

    public RankingCarryOverScheduler(
            RankingRepository rankingRepository,
            @Value("${ranking.carry-over.weight:0.1}") double carryWeight
    ) {
        this.rankingRepository = rankingRepository;
        this.carryWeight = carryWeight;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        String sourceKey = yesterday.format(DATE_KEY_FORMAT);
        String destKey = today.format(DATE_KEY_FORMAT);

        log.info("[RankingCarryOver] 시작 - source={}, dest={}, weight={}", sourceKey, destKey, carryWeight);
        rankingRepository.carryOver(sourceKey, destKey, carryWeight);
        log.info("[RankingCarryOver] 완료");
    }
}

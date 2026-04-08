package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 자정 직후 전일 랭킹 일부를 당일 키로 이월(carryover)하는 설정.
 * <p>
 * 목적: 날짜 경계(자정) 직후 당일 키가 비어 순위가 급변(0 → N)하는 UX를 완화한다.
 * 구현은 "전일 Top N을 낮은 가중치로 복사"이며, 이후 실시간/보정 경로의 매트릭 기반 ZADD로 자연스럽게 수렴한다.
 */
@ConfigurationProperties(prefix = "collector.ranking-carryover")
public record RankingCarryoverProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 0 0 * * ?") String cron,
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("50") int topN,
        @DefaultValue("0.1") double weight,
        @DefaultValue("true") boolean onlyWhenTodayEmpty
) {
}


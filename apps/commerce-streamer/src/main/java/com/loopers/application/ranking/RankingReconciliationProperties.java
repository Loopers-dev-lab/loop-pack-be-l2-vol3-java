package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 랭킹 보정 배치 스케줄 설정.
 *
 * @param enabled {@code true}일 때만 스케줄이 동작한다(기본 비활성).
 * @param cron Spring cron 표현식
 * @param zone 스케줄 타임존
 * @param batchSize product_metrics 페이지 조회 크기
 */
@ConfigurationProperties(prefix = "collector.ranking-reconciliation")
public record RankingReconciliationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 0 2 * * ?") String cron,
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("500") int batchSize
) {
}

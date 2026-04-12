package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;

/**
 * R9 랭킹 파이프라인 부속 설정:
 * - `@EnableScheduling` — {@link com.loopers.application.ranking.RankingCarryOverScheduler} 활성화
 * - `Clock` 빈 — 서비스/스케줄러가 시간 주입을 받아 테스트에서 고정 가능
 * - `TransactionTemplate` 빈 — {@link com.loopers.application.ranking.RankingAggregationService} 가
 *   `@Transactional` 자기 호출 함정을 회피하기 위해 명시적으로 트랜잭션을 연다.
 *
 * `@ConfigurationProperties` 바인딩은 {@code CommerceStreamerApplication} 의
 * `@ConfigurationPropertiesScan` 에 의해 자동 등록된다.
 */
@Configuration
@EnableScheduling
public class RankingConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public TransactionTemplate rankingTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}

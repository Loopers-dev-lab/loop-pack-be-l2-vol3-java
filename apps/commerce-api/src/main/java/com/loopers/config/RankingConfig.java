package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * R9 랭킹 읽기 경로 부속 설정 — {@link com.loopers.application.ranking.RankingFacade} 가
 * "오늘 날짜" 를 KST 기준으로 주입받을 수 있도록 `Clock` 빈을 제공한다.
 *
 * 테스트에서는 이 빈을 `Clock.fixed(...)` 로 오버라이드하여 시간을 고정할 수 있다.
 */
@Configuration
public class RankingConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}

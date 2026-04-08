package com.loopers;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import com.loopers.application.ranking.RankingCarryoverProperties;
import com.loopers.application.ranking.RankingReconciliationProperties;
import com.loopers.infrastructure.metrics.CollectorLagProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@ConfigurationPropertiesScan
@EnableConfigurationProperties({
        CollectorLagProperties.class,
        RankingReconciliationProperties.class,
        RankingCarryoverProperties.class
})
@SpringBootApplication
public class CommerceStreamerApplication {
    @PostConstruct
    public void started() {
        // set timezone
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CommerceStreamerApplication.class, args);
    }
}



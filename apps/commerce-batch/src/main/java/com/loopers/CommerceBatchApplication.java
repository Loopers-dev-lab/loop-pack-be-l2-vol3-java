package com.loopers;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CommerceBatchApplication {
    private static final String DEFAULT_ZONE_ID = "Asia/Seoul";

    @PostConstruct
    public void started() {
        // set timezone
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_ZONE_ID));
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(DEFAULT_ZONE_ID));
    }

    public static void main(String[] args) {
        int exitCode = SpringApplication.exit(SpringApplication.run(CommerceBatchApplication.class, args));
        System.exit(exitCode);
    }
}

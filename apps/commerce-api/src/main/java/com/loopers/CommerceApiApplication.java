package com.loopers;

import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.IntStream;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class CommerceApiApplication {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired(required = false)
    private RedisProductRankingRepository redisProductRankingRepository;

    @PostConstruct
    public void started() {
        // set timezone
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpPeriodicRankings() {
        if (redisProductRankingRepository == null) {
            return;
        }
        LocalDate today = LocalDate.now(KOREA_ZONE);
        List<LocalDate> weeklyStarts = IntStream.range(0, 10)
                .mapToObj(week -> today.minusWeeks(week).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
                .distinct()
                .toList();
        List<LocalDate> monthlyStarts = IntStream.range(0, 12)
                .mapToObj(month -> today.minusMonths(month).withDayOfMonth(1))
                .distinct()
                .toList();
        redisProductRankingRepository.warmUpWeeklyRankings(weeklyStarts);
        redisProductRankingRepository.warmUpMonthlyRankings(monthlyStarts);
    }

    public static void main(String[] args) {
        SpringApplication.run(CommerceApiApplication.class, args);
    }
}

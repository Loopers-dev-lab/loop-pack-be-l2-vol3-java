package com.loopers.application.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RankingKeyGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOURLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";

    private final RankingProperties properties;

    public String generateDailyKey(LocalDate date) {
        return properties.keyPrefix() + date.format(DATE_FORMATTER);
    }

    public String generateTodayKey() {
        return generateDailyKey(LocalDate.now());
    }

    public String generateHourlyKey(LocalDateTime dateTime) {
        return HOURLY_KEY_PREFIX + dateTime.format(HOURLY_FORMATTER);
    }

    public String generateCurrentHourKey() {
        return generateHourlyKey(LocalDateTime.now());
    }

    public int getTtlDays() {
        return properties.ttlDays();
    }

    public int getHourlyTtlHours() {
        return 3;
    }

    public LocalDate parseDateFromKey(String key) {
        String dateStr = key.substring(properties.keyPrefix().length());
        return LocalDate.parse(dateStr, DATE_FORMATTER);
    }
}

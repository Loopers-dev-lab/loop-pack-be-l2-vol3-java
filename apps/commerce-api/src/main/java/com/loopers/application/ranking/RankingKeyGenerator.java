package com.loopers.application.ranking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RankingKeyGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final ZoneId zoneId;

    public RankingKeyGenerator(@Value("${commerce.ranking.zone-id:Asia/Seoul}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public String dailyKey(LocalDate date) {
        return "ranking:all:" + DATE_FORMATTER.format(date);
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public String hourlyKey(LocalDateTime hour) {
        return "ranking:all:hour:" + HOUR_FORMATTER.format(hour);
    }

    public LocalDateTime currentHour() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.withMinute(0).withSecond(0).withNano(0).toLocalDateTime();
    }
}

package com.loopers.application.collector;

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

    public String dailyKey(ZonedDateTime occurredAt) {
        LocalDate localDate = occurredAt.withZoneSameInstant(zoneId).toLocalDate();
        return dailyKey(localDate);
    }

    public String dailyKey(LocalDate localDate) {
        return "ranking:all:" + DATE_FORMATTER.format(localDate);
    }

    public String hourlyKey(ZonedDateTime occurredAt) {
        LocalDateTime localDateTime = occurredAt.withZoneSameInstant(zoneId).toLocalDateTime();
        return hourlyKey(localDateTime);
    }

    public String hourlyKey(LocalDateTime localDateTime) {
        return "ranking:all:hour:" + HOUR_FORMATTER.format(localDateTime);
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }
}

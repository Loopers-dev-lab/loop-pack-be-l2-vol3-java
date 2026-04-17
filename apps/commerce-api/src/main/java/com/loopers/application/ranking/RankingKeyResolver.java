package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

@Component
public class RankingKeyResolver {

    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final Clock clock;

    public RankingKeyResolver(Clock clock) {
        this.clock = clock;
    }

    public String resolve(RankingPeriod period, LocalDate date, String groupName) {
        String base = switch (period) {
            case REALTIME -> "ranking:realtime";
            case DAILY -> "ranking:daily:" + date.format(DAILY_FORMAT);
            case WEEKLY -> {
                WeekFields iso = WeekFields.ISO;
                int year = date.get(iso.weekBasedYear());
                int week = date.get(iso.weekOfWeekBasedYear());
                yield String.format("ranking:weekly:%d%02d", year, week);
            }
            case MONTHLY -> "ranking:monthly:" + date.format(MONTHLY_FORMAT);
        };
        return base + ":" + groupName;
    }

    public String resolve(RankingPeriod period, LocalDate date) {
        return resolve(period, date, "control");
    }
}

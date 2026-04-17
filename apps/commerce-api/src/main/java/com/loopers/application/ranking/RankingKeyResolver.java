package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class RankingKeyResolver {

    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Clock clock;

    public RankingKeyResolver(Clock clock) {
        this.clock = clock;
    }

    public String resolve(RankingPeriod period, LocalDate date, String groupName) {
        String base = switch (period) {
            case REALTIME -> "ranking:realtime";
            case DAILY -> "ranking:daily:" + date.format(DAILY_FORMAT);
            // LAST_7D / LAST_30D 는 anchor = 어제 (오늘 제외 롤링 윈도우) 기반 키
            case LAST_7D  -> "ranking:last7d:"  + anchorDateKey(date);
            case LAST_30D -> "ranking:last30d:" + anchorDateKey(date);
        };
        return base + ":" + groupName;
    }

    public String resolve(RankingPeriod period, LocalDate date) {
        return resolve(period, date, "control");
    }

    /**
     * 조회 기준일(오늘) 로부터 anchor_date (= 어제) 를 반환한다.
     * 배치가 이 anchor 로 MV / Redis ZSET 을 만들므로 API 도 동일 키로 조회해야 한다.
     */
    public LocalDate anchorDateOf(LocalDate date) {
        return date.minusDays(1);
    }

    private String anchorDateKey(LocalDate date) {
        return anchorDateOf(date).format(DAILY_FORMAT);
    }
}

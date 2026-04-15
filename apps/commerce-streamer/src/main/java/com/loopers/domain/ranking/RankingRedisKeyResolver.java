package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * occurredAt 기준으로 일간 랭킹 Redis 키를 계산한다.
 * <p>
 * 계약 고정 단계 결정에 따라 날짜 귀속은 {@code occurredAt + Asia/Seoul}을 사용한다.
 */
public class RankingRedisKeyResolver {

    private static final String DAILY_KEY_PREFIX = "ranking:all:";
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * occurredAt를 한국 시간대로 변환해 {@code ranking:all:{yyyyMMdd}} 키를 생성한다.
     *
     * @param occurredAt 이벤트 발생 시각(UTC Instant)
     * @return 일간 랭킹 Redis 키
     * @throws IllegalArgumentException occurredAt가 null인 경우
     */
    public String resolveDailyAllKey(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        String datePart = DATE_FORMATTER.format(occurredAt.atZone(KOREA_ZONE_ID).toLocalDate());
        return DAILY_KEY_PREFIX + datePart;
    }
}

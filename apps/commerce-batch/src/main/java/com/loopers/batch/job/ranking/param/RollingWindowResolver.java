package com.loopers.batch.job.ranking.param;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * JobParameter 로 받은 anchorDate(yyyyMMdd) 를 {@link RollingWindow} 로 변환한다.
 * 외부 플랫폼(Cron/K8s/Airflow) 이 주입한 값만 신뢰하며 {@code LocalDate.now()} 같은
 * 트리거 시간 의존은 허용하지 않는다 (트리거 시간 != 데이터 경계).
 */
public final class RollingWindowResolver {

    // STRICT resolver 로 "20260230" 같은 무효 날짜를 예외 처리 (SMART 는 관대하게 보정함)
    private static final DateTimeFormatter KEY_FORMAT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private RollingWindowResolver() {
    }

    public static RollingWindow resolve(String anchorDateKey) {
        if (anchorDateKey == null || anchorDateKey.isBlank()) {
            throw new IllegalArgumentException("anchorDate 파라미터가 필요합니다 (yyyyMMdd)");
        }
        LocalDate anchorDate;
        try {
            anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "anchorDate 포맷이 잘못되었습니다 (yyyyMMdd): " + anchorDateKey, e);
        }
        return RollingWindow.of(anchorDate);
    }
}

package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 일간 랭킹 ZSET 키 계약 ({@code ranking:all:{yyyyMMdd}}).
 * <p>
 * 읽기 API는 조회 일자(기본: 오늘, Asia/Seoul)에 맞춰 <strong>동적</strong>으로 키를 구성한다.
 * ZSET member에는 상품 ID만 두고, 상품명·가격 등은 Hydration 단계에서 결합한다(설계 §4.2).
 */
public final class RankingKey {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingKey() {
    }

    /**
     * 일간 랭킹 ZSET 키를 생성한다.
     *
     * @param localDate 일자 (기본: 오늘(Asia/Seoul))
     * @return 일간 랭킹 ZSET 키
     */
    public static String dailyAll(LocalDate localDate) {
        if (localDate == null) {
            throw new IllegalArgumentException("localDate must not be null");
        }
        return "ranking:all:" + DATE.format(localDate);
    }

    /**
     * 일간 랭킹 ZSET의 읽기 전용 스냅샷 키 ({@code ranking:snap:{yyyyMMdd}:{uuid}}).
     *
     * @param localDate    일자
     * @param snapshotUuid {@link java.util.UUID} 정규 문자열(이미 검증된 값)
     * @return 스냅샷 ZSET 키
     */
    public static String snapshot(LocalDate localDate, String snapshotUuid) {
        if (localDate == null) {
            throw new IllegalArgumentException("localDate must not be null");
        }
        if (snapshotUuid == null || snapshotUuid.isBlank()) {
            throw new IllegalArgumentException("snapshotUuid must not be blank");
        }
        return "ranking:snap:" + DATE.format(localDate) + ":" + snapshotUuid;
    }
}

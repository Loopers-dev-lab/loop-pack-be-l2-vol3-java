package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 합성 score 인코더.
 *
 * <p>Tie-Break 정책: 동일 base points 일 때 더 최근에 갱신된 상품이 상위.
 *
 * <pre>
 *   composite = basePoints + ((lastScoredEpochSec - bucketStartEpochSec) * 1e-9)
 * </pre>
 *
 * <ul>
 *   <li>bucketStart: DAY 버킷이면 그 날 자정(KST), HOUR 버킷이면 그 시간 정시</li>
 *   <li>경과초는 DAY 0~86399, HOUR 0~3599 → 1e-9 곱해서 소수 8~9자리에 시각 정보가 들어감</li>
 *   <li>basePoints가 약 1e6 이하일 때 double 정밀도(약 15자리) 안에서 안전하게 보존</li>
 *   <li>basePoints 차이가 1e-4 이상이면 tie-break 비트는 무시되어 정상 정렬</li>
 * </ul>
 */
public final class RankingScoreEncoder {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHH");
    private static final double TIE_BREAK_SCALE = 1e-9;

    private RankingScoreEncoder() {
    }

    public static double encode(RankingScoreLedger ledger) {
        long bucketStartSec = bucketStartEpochSec(ledger.getBucketType(), ledger.getBucketKey());
        long lastSec = ledger.getLastScoredAt().getEpochSecond();
        long elapsed = Math.max(0L, lastSec - bucketStartSec);
        return ledger.getBasePoints() + (elapsed * TIE_BREAK_SCALE);
    }

    static long bucketStartEpochSec(RankingScoreLedger.BucketType type, String bucketKey) {
        return switch (type) {
            case DAY -> LocalDate.parse(bucketKey, DAY_FORMAT)
                .atStartOfDay(KST)
                .toEpochSecond();
            case HOUR -> LocalDateTime.parse(bucketKey, HOUR_FORMAT)
                .atZone(KST)
                .toEpochSecond();
        };
    }

    /**
     * 디버깅/테스트용: composite score에서 base points를 근사 추출.
     * 정확한 base points가 필요하면 ledger 테이블을 직접 조회할 것.
     */
    public static double decodeBase(double composite) {
        return Math.floor(composite * 1e4) / 1e4;
    }

    /**
     * 테스트 헬퍼: ledger 없이 임의 시각으로 인코딩 결과 계산.
     */
    public static double encode(RankingScoreLedger.BucketType type, String bucketKey, double basePoints, Instant lastScoredAt) {
        long bucketStart = bucketStartEpochSec(type, bucketKey);
        long elapsed = Math.max(0L, lastScoredAt.getEpochSecond() - bucketStart);
        return basePoints + (elapsed * TIE_BREAK_SCALE);
    }
}

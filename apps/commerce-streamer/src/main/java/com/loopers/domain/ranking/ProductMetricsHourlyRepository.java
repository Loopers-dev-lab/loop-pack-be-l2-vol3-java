package com.loopers.domain.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * `product_metrics_hourly` 저장소 인터페이스 (DIP).
 *
 * 쓰기: {@link #upsertIncrements(Long, LocalDateTime, long, long, long, BigDecimal)}
 *       → Native `INSERT ... ON DUPLICATE KEY UPDATE`
 *
 * 읽기: {@link #snapshotByDate(Long, LocalDate)}
 *       → 지정 일자의 모든 bucket 을 상품 단위로 집계
 */
public interface ProductMetricsHourlyRepository {

    /**
     * 지정 (productId, bucketHour) row 에 델타를 합산한다.
     *
     * - INSERT 분기: 음수 좋아요/금액 방지를 위해 VALUES 단계에서 {@code GREATEST(..., 0)} clamp
     * - UPDATE 분기: 기존 값 + 델타, 음수 방지 동일
     *
     * @param productId    상품 ID
     * @param bucketHour   시간 단위로 절삭된 시각 (분/초 = 0)
     * @param viewDelta    뷰 증분 (음수 불가)
     * @param likeDelta    좋아요 증분 (-1 허용, 음수 clamp)
     * @param orderDelta   주문 수량 증분 (음수 불가)
     * @param amountDelta  주문 금액 증분 (음수 불가)
     */
    void upsertIncrements(
            Long productId,
            LocalDateTime bucketHour,
            long viewDelta,
            long likeDelta,
            long orderDelta,
            BigDecimal amountDelta
    );

    /**
     * 특정 상품의 지정 일자 전체 bucket 을 합산한 스냅샷을 반환한다.
     * 매칭 row 가 없으면 모든 필드 0 인 empty 스냅샷을 반환한다.
     */
    ProductDailyAggregate snapshotByDate(Long productId, LocalDate date);
}

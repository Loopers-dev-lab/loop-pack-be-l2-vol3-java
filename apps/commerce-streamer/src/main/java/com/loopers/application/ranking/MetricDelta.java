package com.loopers.application.ranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 배치 poll() 안에서 한 상품에 대해 집계된 증분 델타 VO.
 *
 * Consumer 배치 aggregate 단계에서 생성되어, `RankingAggregationService.processBatch` 로 전달된다.
 * 하나의 poll 안에 같은 상품이 여러 번 등장해도 이 구조로 합산되어
 * DB UPSERT / ZSET 쓰기가 "N건 → 1회" 로 압축된다.
 *
 * <p>멤버 필드:
 * <ul>
 *   <li>view  += 1 per PRODUCT_VIEWED</li>
 *   <li>like  += 1 per PRODUCT_LIKED (liked=true), -=1 per (liked=false)</li>
 *   <li>order += quantity per ORDER_PAID line</li>
 *   <li>amount += unitPrice * quantity per ORDER_PAID line</li>
 *   <li>eventIds : 멱등성 체크용 — 배치 내 중복/재수신 방어</li>
 * </ul>
 */
public class MetricDelta {

    private long view;
    private long like;
    private long order;
    private BigDecimal amount = BigDecimal.ZERO;
    private final List<String> eventIds = new ArrayList<>();

    public void addView(long delta) {
        this.view += delta;
    }

    public void addLike(long delta) {
        this.like += delta;
    }

    public void addOrder(long quantity, BigDecimal lineAmount) {
        this.order += quantity;
        if (lineAmount != null) {
            this.amount = this.amount.add(lineAmount);
        }
    }

    public void rememberEventId(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            this.eventIds.add(eventId);
        }
    }

    public long view() {
        return view;
    }

    public long like() {
        return like;
    }

    public long order() {
        return order;
    }

    public BigDecimal amount() {
        return amount;
    }

    public List<String> eventIds() {
        return Collections.unmodifiableList(eventIds);
    }

    /**
     * 모든 누적값이 0 인 상태인지 확인한다.
     *
     * <p>주의: like 가 +1/-1 로 상쇄되어 0 이 되면 isEmpty=true 로 판정되어 DB UPSERT 가
     * 스킵된다. 이 경우 점수 변화가 없으므로 정상 동작이며, 해당 eventId 들은 호출자(`persistDeltas`)
     * 에서 `event_handled` 에 저장되어 재처리되지 않는다.
     */
    public boolean isEmpty() {
        return view == 0 && like == 0 && order == 0 && amount.signum() == 0;
    }
}

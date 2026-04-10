package com.loopers.application.ranking;

/**
 * 이벤트 배치에서 productId별로 집계된 메트릭 변화량.
 *
 * <p>모든 필드는 DB의 additive 컬럼에 대응하며 항상 0 이상이다.
 * Redis用 net delta는 파생 getter로 제공한다.</p>
 *
 * <ul>
 *   <li>DB (Phase 2): {@code getLikeDelta()}, {@code getUnlikeDelta()} 등 → 양수 누적</li>
 *   <li>Redis (Phase 3): {@code getNetLikeDelta()} 등 → {@code likeDelta - unlikeDelta} (음수 가능)</li>
 * </ul>
 */
public class MetricsDelta {

    private int viewDelta;
    private int likeDelta;
    private int unlikeDelta;
    private int salesCountDelta;
    private long salesAmountDelta;
    private int cancelCountDelta;
    private long cancelAmountDelta;

    // ── DB用 getters (additive, ≥ 0) ──

    public int getViewDelta() { return viewDelta; }
    public int getLikeDelta() { return likeDelta; }
    public int getUnlikeDelta() { return unlikeDelta; }
    public int getSalesCountDelta() { return salesCountDelta; }
    public long getSalesAmountDelta() { return salesAmountDelta; }
    public int getCancelCountDelta() { return cancelCountDelta; }
    public long getCancelAmountDelta() { return cancelAmountDelta; }

    // ── Redis用 net delta getters (HINCRBY에 전달, 음수 가능) ──

    public int getNetLikeDelta() { return likeDelta - unlikeDelta; }
    public int getNetSalesCountDelta() { return salesCountDelta - cancelCountDelta; }
    public long getNetSalesAmountDelta() { return salesAmountDelta - cancelAmountDelta; }

    // ── factory methods ──

    public static MetricsDelta ofView() {
        MetricsDelta d = new MetricsDelta();
        d.viewDelta = 1;
        return d;
    }

    public static MetricsDelta ofLike() {
        MetricsDelta d = new MetricsDelta();
        d.likeDelta = 1;
        return d;
    }

    public static MetricsDelta ofUnlike() {
        MetricsDelta d = new MetricsDelta();
        d.unlikeDelta = 1;
        return d;
    }

    public static MetricsDelta ofSales(int count, long amount) {
        MetricsDelta d = new MetricsDelta();
        d.salesCountDelta = count;
        d.salesAmountDelta = amount;
        return d;
    }

    public static MetricsDelta ofCancel(int count, long amount) {
        MetricsDelta d = new MetricsDelta();
        d.cancelCountDelta = count;
        d.cancelAmountDelta = amount;
        return d;
    }

    public static MetricsDelta merge(MetricsDelta a, MetricsDelta b) {
        MetricsDelta result = new MetricsDelta();
        result.viewDelta = a.viewDelta + b.viewDelta;
        result.likeDelta = a.likeDelta + b.likeDelta;
        result.unlikeDelta = a.unlikeDelta + b.unlikeDelta;
        result.salesCountDelta = a.salesCountDelta + b.salesCountDelta;
        result.salesAmountDelta = a.salesAmountDelta + b.salesAmountDelta;
        result.cancelCountDelta = a.cancelCountDelta + b.cancelCountDelta;
        result.cancelAmountDelta = a.cancelAmountDelta + b.cancelAmountDelta;
        return result;
    }
}

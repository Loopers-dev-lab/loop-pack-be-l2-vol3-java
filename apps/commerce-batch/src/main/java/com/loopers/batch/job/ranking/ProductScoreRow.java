package com.loopers.batch.job.ranking;

/**
 * JdbcCursorItemReader 집계 결과를 담는 DTO.
 * product_metrics_daily GROUP BY 결과 → (productId, counts, totalScore)
 */
public class ProductScoreRow {

    private Long productId;
    private long viewCount;
    private long likeCount;
    private long orderCount;
    private double totalScore;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }
    public double getTotalScore() { return totalScore; }
    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
}

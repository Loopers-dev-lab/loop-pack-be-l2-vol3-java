package com.loopers.batch.job.ranking.step;

public class RankedProductDto {

    private final Long productId;
    private final double score;

    public RankedProductDto(Long productId, double score) {
        this.productId = productId;
        this.score = score;
    }

    public Long getProductId() {
        return productId;
    }

    public double getScore() {
        return score;
    }
}

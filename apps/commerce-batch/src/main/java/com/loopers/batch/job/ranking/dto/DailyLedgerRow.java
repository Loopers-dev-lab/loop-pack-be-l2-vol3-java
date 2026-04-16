package com.loopers.batch.job.ranking.dto;

public record DailyLedgerRow(Long productId, String bucketKey, double basePoints) {
}

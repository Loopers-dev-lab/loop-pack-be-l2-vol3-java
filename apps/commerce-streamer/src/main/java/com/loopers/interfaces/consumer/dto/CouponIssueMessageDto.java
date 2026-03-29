package com.loopers.interfaces.consumer.dto;

public class CouponIssueMessageDto {

    public record CouponIssueMessage(Long couponId, Long userId, String requestedAt) {}
}

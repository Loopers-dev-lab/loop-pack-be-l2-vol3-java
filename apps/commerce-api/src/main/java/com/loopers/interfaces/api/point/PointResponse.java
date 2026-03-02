package com.loopers.interfaces.api.point;

public class PointResponse {

    public record PointBalanceResponse(Long userId, int balance) {}
}

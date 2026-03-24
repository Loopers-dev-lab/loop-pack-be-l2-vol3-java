package com.loopers.domain.like;

public class LikeEvent {

    public record Created(Long userId, Long productId) {}

    public record Deleted(Long userId, Long productId) {}
}

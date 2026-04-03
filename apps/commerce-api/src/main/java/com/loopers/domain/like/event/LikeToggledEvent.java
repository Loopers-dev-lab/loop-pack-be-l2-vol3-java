package com.loopers.domain.like.event;

public record LikeToggledEvent(
    Long productId,
    boolean liked
) {}

package com.loopers.domain.favorite.model;

public final class FavoriteCommand {

    private FavoriteCommand() {}

    public record Add(Long memberId, Long productId) {}

    public record Delete(Long memberId, Long productId) {}
}

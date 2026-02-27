package com.loopers.domain.brand.model;

public final class BrandCommand {

    private BrandCommand() {}

    public record Create(String name, String description) {}

    public record Update(String name, String description) {}
}

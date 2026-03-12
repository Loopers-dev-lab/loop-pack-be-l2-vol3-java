package com.loopers.domain.brand.dto;

public class BrandCommand {

    public record Register(String name) {
    }

    public record Update(String name) {
    }
}

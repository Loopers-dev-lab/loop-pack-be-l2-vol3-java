package com.loopers.application.brand;

public record BrandUpdateCommand(
        Long id,
        String name
) {}

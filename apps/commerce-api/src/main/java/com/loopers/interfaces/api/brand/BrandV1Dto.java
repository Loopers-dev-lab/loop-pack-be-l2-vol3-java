package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;

public class BrandV1Dto {

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(BrandModel model) {
            return new BrandResponse(model.getId(), model.name().value(), model.description());
        }
    }
}

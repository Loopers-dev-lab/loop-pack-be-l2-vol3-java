package com.loopers.interfaces.brand.dto;

import com.loopers.application.brand.dto.BrandResult;

public class BrandV1Dto {

    public record DetailResponse(
        Long id,
        String name
    ) {
        public static DetailResponse from(BrandResult info) {
            return new DetailResponse(info.id(), info.name());
        }
    }
}

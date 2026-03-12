package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandAdminV1Dto {

    public record CreateRequest(String name, String description) {}

    public record UpdateRequest(String name, String description) {}

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }
}

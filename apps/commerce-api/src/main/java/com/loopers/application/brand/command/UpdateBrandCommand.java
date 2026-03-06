package com.loopers.application.brand.command;

import lombok.Builder;

@Builder
public record UpdateBrandCommand(
        String description,
        String imageUrl
) {
    @Override
    public String toString() {
        return "UpdateBrandCommand[description=%s, imageUrl=%s]"
                .formatted(description, imageUrl);
    }
}

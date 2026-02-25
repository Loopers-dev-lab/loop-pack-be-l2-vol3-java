package com.loopers.application.brand.command;

import lombok.Builder;

@Builder
public record CreateBrandCommand(
        String name,
        String description,
        String imageUrl
) {
    @Override
    public String toString() {
        return "CreateBrandCommand[name=%s, description=%s, imageUrl=%s]"
                .formatted(name, description, imageUrl);
    }
}

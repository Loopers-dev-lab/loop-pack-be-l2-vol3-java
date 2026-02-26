package com.loopers.domain.model.brand.event;

import com.loopers.domain.model.common.DomainEvent;

import java.time.LocalDateTime;

public record BrandDeletedEvent(
        Long brandId,
        LocalDateTime occurredAt
) implements DomainEvent {

    public BrandDeletedEvent(Long brandId) {
        this(brandId, LocalDateTime.now());
    }
}

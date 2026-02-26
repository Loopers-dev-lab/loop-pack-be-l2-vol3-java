package com.loopers.domain.model.common;

import java.time.LocalDateTime;

public interface DomainEvent {

    LocalDateTime occurredAt();
}

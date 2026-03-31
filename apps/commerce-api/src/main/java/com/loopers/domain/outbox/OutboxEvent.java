package com.loopers.domain.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(staticName = "of")
public class OutboxEvent {
    private final Outbox outbox;
}

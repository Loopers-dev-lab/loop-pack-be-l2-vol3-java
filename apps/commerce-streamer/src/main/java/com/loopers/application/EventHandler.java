package com.loopers.application;

import com.loopers.event.Event;
import com.loopers.event.EventPayload;

public interface EventHandler<T extends EventPayload> {
    boolean supports(Event<EventPayload> event);
    void handle(Event<T> event);
}

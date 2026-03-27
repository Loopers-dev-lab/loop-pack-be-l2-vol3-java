package com.loopers.domain.idempotent;

import java.time.ZonedDateTime;

public interface EventHandledRepository {

    boolean existsByEventId(String eventId);

    EventHandled save(EventHandled eventHandled);

    void deleteHandledBefore(ZonedDateTime before);
}

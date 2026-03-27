package com.loopers.domain.log;

import java.time.ZonedDateTime;

public interface EventLogRepository {

    EventLog save(EventLog eventLog);

    void deleteLogsBefore(ZonedDateTime before);
}

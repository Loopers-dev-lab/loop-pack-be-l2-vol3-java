package com.loopers.infrastructure.log;

import com.loopers.domain.log.EventLog;
import com.loopers.domain.log.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
@RequiredArgsConstructor
public class EventLogRepositoryImpl implements EventLogRepository {

    private final EventLogJpaRepository jpaRepository;

    @Override
    public EventLog save(EventLog eventLog) {
        return jpaRepository.save(eventLog);
    }

    @Override
    public void deleteLogsBefore(ZonedDateTime before) {
        jpaRepository.deleteByCreatedAtBefore(before);
    }
}

package com.loopers.infrastructure.idempotent;

import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return jpaRepository.save(eventHandled);
    }

    @Override
    public void deleteHandledBefore(ZonedDateTime before) {
        jpaRepository.deleteByHandledAtBefore(before);
    }
}

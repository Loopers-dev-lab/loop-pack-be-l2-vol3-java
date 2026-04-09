package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }

    @Override
    public int insertIgnore(String eventId, ZonedDateTime occurredAt) {
        return eventHandledJpaRepository.insertIgnore(eventId, occurredAt);
    }

    @Override
    public Set<String> findExistingEventIds(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> found = eventHandledJpaRepository.findEventIdsByEventIdIn(eventIds);
        return new HashSet<>(found);
    }

    @Override
    public int bulkInsertIgnore(List<EventHandledRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder(
                "INSERT IGNORE INTO event_handled (event_id, occurred_at, handled_at) VALUES ");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(:eventId").append(i)
                    .append(", :occurredAt").append(i)
                    .append(", NOW())");
        }

        Query q = em.createNativeQuery(sb.toString());
        for (int i = 0; i < records.size(); i++) {
            EventHandledRecord r = records.get(i);
            q.setParameter("eventId" + i, r.eventId());
            q.setParameter("occurredAt" + i,
                    r.occurredAt() == null ? null : Timestamp.from(r.occurredAt().toInstant()));
        }
        return q.executeUpdate();
    }
}

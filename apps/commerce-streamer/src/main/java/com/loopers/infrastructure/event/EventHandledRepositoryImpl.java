package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// EventHandledRepository 구현체
@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final EntityManager entityManager;

    @Override
    public boolean existsByEventId(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }

    @Override
    public Set<String> findExistingEventIds(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Set.of();
        }
        List<EventHandled> found = eventHandledJpaRepository.findAllById(eventIds);
        Set<String> existing = new HashSet<>(found.size());
        for (EventHandled e : found) {
            existing.add(e.getEventId());
        }
        return existing;
    }

    /**
     * 한 SQL 문에 묶을 multi-row VALUES 의 최대 크기.
     * MySQL `max_allowed_packet` (default 64MB) 와 안전한 executeUpdate 시간을 고려한 보수값.
     */
    private static final int MAX_BATCH = 500;

    @Override
    public void saveAllNew(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return;

        // 빈/널 필터 + 청크 분할 — 한 번의 INSERT 로 다중 row 삽입한다.
        List<String> filtered = new ArrayList<>(eventIds.size());
        for (String eventId : eventIds) {
            if (eventId != null && !eventId.isBlank()) {
                filtered.add(eventId);
            }
        }
        if (filtered.isEmpty()) return;

        for (int i = 0; i < filtered.size(); i += MAX_BATCH) {
            List<String> chunk = filtered.subList(i, Math.min(i + MAX_BATCH, filtered.size()));
            executeMultiRowInsert(chunk);
        }
    }

    private void executeMultiRowInsert(List<String> chunk) {
        // 위치 파라미터(`?N`) 로 다중 row VALUES 를 동적 구성. (event_id, NOW()) 반복.
        StringBuilder sql = new StringBuilder("INSERT IGNORE INTO event_handled (event_id, handled_at) VALUES ");
        for (int idx = 0; idx < chunk.size(); idx++) {
            if (idx > 0) sql.append(", ");
            sql.append("(?").append(idx + 1).append(", NOW())");
        }
        Query query = entityManager.createNativeQuery(sql.toString());
        for (int idx = 0; idx < chunk.size(); idx++) {
            query.setParameter(idx + 1, chunk.get(idx));
        }
        query.executeUpdate();
    }
}

package com.loopers.fake;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake EventHandledRepository.
 */
public class FakeEventHandledRepository implements EventHandledRepository {

    private final List<EventHandled> store = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public EventHandled save(EventHandled eventHandled) {
        Long newId = idGenerator.incrementAndGet();
        EventHandled saved = EventHandled.reconstitute(
            newId,
            eventHandled.getEventId(),
            eventHandled.getEventType(),
            eventHandled.getHandledAt()
        );
        store.add(saved);
        return saved;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return store.stream().anyMatch(e -> e.getEventId().equals(eventId));
    }

    public List<EventHandled> findAll() {
        return new ArrayList<>(store);
    }

    public void clear() {
        store.clear();
        idGenerator.set(0);
    }
}

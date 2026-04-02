package com.loopers.fake;

import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 테스트용 Fake ApplicationEventPublisher.
 * 발행된 이벤트를 캡처하여 테스트에서 검증 가능.
 */
public class FakeApplicationEventPublisher implements ApplicationEventPublisher {

    private final List<Object> publishedEvents = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        publishedEvents.add(event);
    }

    /**
     * 발행된 모든 이벤트 조회.
     */
    public List<Object> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    /**
     * 특정 타입의 이벤트만 조회.
     */
    public <T> List<T> getEventsOfType(Class<T> eventType) {
        return publishedEvents.stream()
            .filter(eventType::isInstance)
            .map(eventType::cast)
            .collect(Collectors.toList());
    }

    /**
     * 특정 타입의 이벤트 발행 여부 확인.
     */
    public boolean hasEventOfType(Class<?> eventType) {
        return publishedEvents.stream().anyMatch(eventType::isInstance);
    }

    /**
     * 발행된 이벤트 수 조회.
     */
    public int getEventCount() {
        return publishedEvents.size();
    }

    /**
     * 이벤트 초기화.
     */
    public void clear() {
        publishedEvents.clear();
    }
}

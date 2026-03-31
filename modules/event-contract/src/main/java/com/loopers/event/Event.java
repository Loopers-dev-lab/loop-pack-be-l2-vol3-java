package com.loopers.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Event<T extends EventPayload> {

    private Long eventId;
    private EventType type;
    private T payload;

    public static <T extends EventPayload> Event<T> of(Long eventId, EventType type, T payload) {
        Event<T> event = new Event<>();
        event.eventId = eventId;
        event.type = type;
        event.payload = payload;
        return event;
    }

    public String toJson() {
        return DataSerializer.serialize(this);
    }

    @SuppressWarnings("unchecked")
    public static Event<EventPayload> fromJson(String json) {
        EventRaw raw = DataSerializer.deserialize(json, EventRaw.class);
        if (raw == null) {
            return null;
        }
        Event<EventPayload> event = new Event<>();
        event.eventId = raw.getEventId();
        event.type = EventType.from(raw.getType());
        event.payload = DataSerializer.deserialize(raw.getPayload(), event.type.getPayloadClass());
        return event;
    }

    @Getter
    @NoArgsConstructor
    private static class EventRaw {
        private Long eventId;
        private String type;
        private Object payload;
    }
}

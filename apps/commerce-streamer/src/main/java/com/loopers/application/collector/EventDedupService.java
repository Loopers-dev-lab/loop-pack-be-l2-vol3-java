package com.loopers.application.collector;

import com.loopers.domain.collector.EventHandledModel;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventDedupService {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public boolean markIfNotHandled(String eventId, String consumerGroup) {
        if (eventHandledJpaRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup)) {
            return false;
        }

        try {
            eventHandledJpaRepository.save(new EventHandledModel(eventId, consumerGroup));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}

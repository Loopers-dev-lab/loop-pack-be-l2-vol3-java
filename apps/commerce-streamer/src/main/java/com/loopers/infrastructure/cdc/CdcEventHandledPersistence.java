package com.loopers.infrastructure.cdc;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CDC 멱등 INSERT를 별도 트랜잭션으로 분리한다.
 * 동일 바깥 트랜잭션에서 {@link org.springframework.dao.DataIntegrityViolationException}을 catch해도
 * rollback-only로 남지 않도록 한다.
 */
@Service
public class CdcEventHandledPersistence {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    public CdcEventHandledPersistence(EventHandledJpaRepository eventHandledJpaRepository) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(EventHandledModel model) {
        eventHandledJpaRepository.saveAndFlush(model);
    }
}

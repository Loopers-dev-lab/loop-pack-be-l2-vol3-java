package com.loopers.infrastructure;

import com.loopers.domain.EventHandled;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledRepository extends JpaRepository<EventHandled, Long> {
}

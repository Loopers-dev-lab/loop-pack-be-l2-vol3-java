package com.loopers.domain.queue;

/** 단일 대기열(eventId)당 허용되는 최대 대기 인원. 설정 빈으로 주입한다. */
public record WaitingQueueCapacityPolicy(long maxWaiting) {}

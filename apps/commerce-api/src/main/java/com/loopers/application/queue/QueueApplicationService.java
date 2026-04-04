package com.loopers.application.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueDomainService;
import com.loopers.domain.queue.QueuePollingResult;
import com.loopers.domain.queue.QueuePosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueueApplicationService {

    private final QueueDomainService queueDomainService;
    private final EntryTokenRepository entryTokenRepository;
    private final int tps;
    private final int maxQueueSize;
    private final long tokenTtlSeconds;
    private final long statusTtlSeconds;

    public QueueApplicationService(
        QueueDomainService queueDomainService,
        EntryTokenRepository entryTokenRepository,
        @Value("${queue.tps}") int tps,
        @Value("${queue.max-queue-size}") int maxQueueSize,
        @Value("${queue.token-ttl-seconds}") long tokenTtlSeconds,
        @Value("${queue.status-ttl-seconds}") long statusTtlSeconds
    ) {
        this.queueDomainService = queueDomainService;
        this.entryTokenRepository = entryTokenRepository;
        this.tps = tps;
        this.maxQueueSize = maxQueueSize;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.statusTtlSeconds = statusTtlSeconds;
    }

    public QueuePosition enterQueue(Long userId) {
        return queueDomainService.enter(userId, maxQueueSize, tps);
    }

    public QueuePollingResult getPosition(Long userId) {
        return queueDomainService.getPollingResult(userId, tps);
    }

    public void restoreToken(EntryToken token) {
        entryTokenRepository.restore(token, tokenTtlSeconds, statusTtlSeconds);
    }
}

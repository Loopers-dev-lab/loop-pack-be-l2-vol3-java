package com.loopers.application.event;

import com.loopers.domain.queue.service.EntryTokenService;
import com.loopers.domain.queue.service.QueueFeatureFlag;
import com.loopers.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueScheduler {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;
    private final QueueFeatureFlag queueFeatureFlag;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ACTIVE_TOKENS = 100;

    @Scheduled(fixedDelay = 500)
    public void processQueue() {
        if (!queueFeatureFlag.isEnabled()) return;

        Set<String> members = queueService.getTopMembers(MAX_ACTIVE_TOKENS);
        if (members == null || members.isEmpty()) return;

        int currentActive = 0;
        List<Long> needToken = new ArrayList<>();
        for (String memberIdStr : members) {
            Long memberId = Long.parseLong(memberIdStr);
            if (entryTokenService.getToken(memberId) != null) {
                currentActive++;
            } else {
                needToken.add(memberId);
            }
        }

        int available = MAX_ACTIVE_TOKENS - currentActive;
        if (available <= 0) return;

        int issueCount = Math.min(available, BATCH_SIZE);
        int issued = 0;
        for (Long memberId : needToken) {
            if (issued >= issueCount) break;
            entryTokenService.issueToken(memberId);
            issued++;
        }

        if (issued > 0) {
            log.info("대기열 토큰 발급: {}명 (활성: {}/{})", issued, currentActive + issued, MAX_ACTIVE_TOKENS);
        }
    }
}

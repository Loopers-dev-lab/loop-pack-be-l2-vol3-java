package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueProperties;
import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class EntryTokenScheduler {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    /**
     * 100ms마다 대기열에서 N명을 꺼내 입장 토큰을 발급한다.
     *
     * fixedRate = 이전 실행 "시작" 시점 기준 100ms 후 재실행
     * (fixedDelay는 이전 실행 "완료" 후 100ms)
     *
     * 배치 크기(batchSize)는 application.yml의 queue.batch-size에서 읽어온다.
     * 산정 근거: DB 커넥션 풀 40, 주문 200ms, 안전마진 70% = 140 TPS / 10 = 14명
     */
    @Scheduled(fixedRate = 100)
    public void issueTokens() {
        Set<ZSetOperations.TypedTuple<String>> users =
                queueService.popUsers(queueProperties.batchSize());

        if (users == null || users.isEmpty()) {
            return;  // 대기열이 비어있으면 아무것도 안 함
        }

        for (ZSetOperations.TypedTuple<String> user : users) {
            String userId = user.getValue();
            if (userId != null) {
                entryTokenService.issueToken(Long.valueOf(userId));
                log.debug("입장 토큰 발급: userId={}", userId);
            }
        }
    }
}

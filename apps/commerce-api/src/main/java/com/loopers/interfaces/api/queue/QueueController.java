package com.loopers.interfaces.api.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueController {

    private static final double ADMISSION_RATE = 80.0;
    private static final long MAX_QUEUE_SIZE = 48_000;

    private final WaitingQueueRedisRepository waitingQueueRedisRepository;
    private final EntryTokenRedisRepository entryTokenRedisRepository;

    @PostMapping("/enter")
    public ApiResponse<QueueDto.EnterResponse> enter(@AuthMember Member member) {
        Long memberId = member.getId();

        if (entryTokenRedisRepository.exists(memberId)) {
            long ttl = entryTokenRedisRepository.getRemainingTtl(memberId);
            return ApiResponse.success(new QueueDto.EnterResponse(
                "ADMITTED", null, null, ttl, null
            ));
        }

        if (waitingQueueRedisRepository.size() >= MAX_QUEUE_SIZE) {
            return ApiResponse.success(new QueueDto.EnterResponse(
                "QUEUE_FULL", null, null, null, null
            ));
        }

        waitingQueueRedisRepository.add(memberId);
        Long rank = waitingQueueRedisRepository.getRank(memberId);

        if (rank == null) {
            // ZADD 후 스케줄러가 이미 POP한 경우 → 토큰 체크
            if (entryTokenRedisRepository.exists(memberId)) {
                long ttl = entryTokenRedisRepository.getRemainingTtl(memberId);
                return ApiResponse.success(new QueueDto.EnterResponse(
                    "ADMITTED", null, null, ttl, null
                ));
            }
            // 토큰도 없으면 재진입 필요 (다음 폴링에서 처리)
            rank = 0L;
        }

        long position = rank + 1;
        long estimatedWaitSeconds = (long) Math.ceil(position / ADMISSION_RATE);

        return ApiResponse.success(new QueueDto.EnterResponse(
            "QUEUED", position, estimatedWaitSeconds, null, calculatePollInterval(position)
        ));
    }

    @GetMapping("/position")
    public ApiResponse<QueueDto.PositionResponse> position(@AuthMember Member member) {
        Long memberId = member.getId();

        if (entryTokenRedisRepository.exists(memberId)) {
            long ttl = entryTokenRedisRepository.getRemainingTtl(memberId);
            return ApiResponse.success(new QueueDto.PositionResponse(
                "ADMITTED", null, null, null, ttl, null
            ));
        }

        Long rank = waitingQueueRedisRepository.getRank(memberId);
        if (rank == null) {
            return ApiResponse.success(new QueueDto.PositionResponse(
                "NOT_IN_QUEUE", null, null, null, null, null
            ));
        }

        long position = rank + 1;
        long totalQueueSize = waitingQueueRedisRepository.size();
        long estimatedWaitSeconds = (long) Math.ceil(position / ADMISSION_RATE);

        return ApiResponse.success(new QueueDto.PositionResponse(
            "WAITING", position, totalQueueSize, estimatedWaitSeconds, null,
            calculatePollInterval(position)
        ));
    }

    /**
     * 대기 순번에 따라 클라이언트 폴링 주기를 차등 제공한다.
     *
     * <p>position 1~100: 1000ms (곧 입장, 빠른 반응 필요)
     * position 101~1000: 3000ms (중간 대기)
     * position 1001+: 5000ms (입장까지 12초 이상)</p>
     *
     * <p>Redis 부하 감소 효과: 48,000명 기준 24,000→9,800 req/sec (59% 감소)</p>
     */
    static Long calculatePollInterval(long position) {
        if (position <= 100) {
            return 1000L;
        } else if (position <= 1000) {
            return 3000L;
        } else {
            return 5000L;
        }
    }
}

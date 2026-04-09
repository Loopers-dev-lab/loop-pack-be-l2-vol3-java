package com.loopers.interfaces.api.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.queue.QueueSseEmitterRegistry;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private static final double ADMISSION_RATE = 80.0;

    private final long maxQueueSize;
    private final WaitingQueueRedisRepository waitingQueueRedisRepository;
    private final EntryTokenRedisRepository entryTokenRedisRepository;
    private final QueueSseEmitterRegistry sseEmitterRegistry;

    private final Counter enterQueuedCounter;
    private final Counter enterAdmittedCounter;
    private final Counter enterQueueFullCounter;

    public QueueController(
        WaitingQueueRedisRepository waitingQueueRedisRepository,
        EntryTokenRedisRepository entryTokenRedisRepository,
        QueueSseEmitterRegistry sseEmitterRegistry,
        MeterRegistry meterRegistry,
        @Value("${queue.max-size:48000}") long maxQueueSize
    ) {
        this.maxQueueSize = maxQueueSize;
        this.waitingQueueRedisRepository = waitingQueueRedisRepository;
        this.entryTokenRedisRepository = entryTokenRedisRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;

        this.enterQueuedCounter = Counter.builder("queue.enter.status")
            .tag("status", "QUEUED")
            .register(meterRegistry);
        this.enterAdmittedCounter = Counter.builder("queue.enter.status")
            .tag("status", "ADMITTED")
            .register(meterRegistry);
        this.enterQueueFullCounter = Counter.builder("queue.enter.status")
            .tag("status", "QUEUE_FULL")
            .register(meterRegistry);
    }

    @PostMapping("/enter")
    public ApiResponse<QueueDto.EnterResponse> enter(@AuthMember Member member) {
        Long memberId = member.getId();

        if (entryTokenRedisRepository.exists(memberId)) {
            long ttl = entryTokenRedisRepository.getRemainingTtl(memberId);
            enterAdmittedCounter.increment();
            return ApiResponse.success(new QueueDto.EnterResponse(
                "ADMITTED", null, null, ttl, null
            ));
        }

        if (waitingQueueRedisRepository.size() >= maxQueueSize) {
            enterQueueFullCounter.increment();
            return ApiResponse.success(new QueueDto.EnterResponse(
                "QUEUE_FULL", null, null, null, null
            ));
        }

        waitingQueueRedisRepository.add(memberId);
        Long rank = waitingQueueRedisRepository.getRank(memberId);

        if (rank == null) {
            if (entryTokenRedisRepository.exists(memberId)) {
                long ttl = entryTokenRedisRepository.getRemainingTtl(memberId);
                enterAdmittedCounter.increment();
                return ApiResponse.success(new QueueDto.EnterResponse(
                    "ADMITTED", null, null, ttl, null
                ));
            }
            rank = 0L;
        }

        long position = rank + 1;
        long estimatedWaitSeconds = (long) Math.ceil(position / ADMISSION_RATE);

        enterQueuedCounter.increment();
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
     * SSE 기반 실시간 순번 Push.
     *
     * <p>ADMITTED → admitted 이벤트 후 즉시 닫기.
     * NOT_IN_QUEUE → not_in_queue 이벤트 후 즉시 닫기.
     * WAITING → registry.register() 호출 (delta 브로드캐스트 수신).</p>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthMember Member member) {
        Long memberId = member.getId();

        if (entryTokenRedisRepository.exists(memberId)) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event().name("admitted").data(Map.of()));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        Long rank = waitingQueueRedisRepository.getRank(memberId);
        if (rank == null) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event().name("not_in_queue").data(Map.of()));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        long position = rank + 1;
        return sseEmitterRegistry.register(memberId, position);
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

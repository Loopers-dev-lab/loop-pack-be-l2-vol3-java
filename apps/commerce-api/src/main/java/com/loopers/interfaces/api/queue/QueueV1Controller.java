package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.QueueSseRegistry;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RestController
public class QueueV1Controller implements QueueV1ApiSpec {

    // SSE timeout: 5분. 이후 클라이언트가 재연결해야 함.
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final QueueService queueService;
    private final QueueSseRegistry sseRegistry;

    /**
     * 대기열 진입.
     * 상류 stateless: 서버가 연결 상태를 유지하지 않음 → 수평 확장 자유.
     * Back-pressure 관점: 10만 요청을 수용하되 실제 처리를 지연시키는 역할.
     */
    @PostMapping("/api/v1/queue/enter")
    @Override
    public ApiResponse<QueueV1Dto.EnterResponse> enter(@RequestBody QueueV1Dto.EnterRequest request) {
        QueueInfo.EnterInfo info = queueService.enter(request.userId(), request.queueId());
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    /**
     * 대기열 상태 폴링.
     * pollIntervalHint 필드로 서버가 권고 주기 제공.
     * Jitter는 클라이언트 책임 (서버는 hint로 권고 가능하나 강제 불가).
     */
    @GetMapping("/api/v1/queue/status")
    @Override
    public ApiResponse<QueueV1Dto.StatusResponse> getStatus(
        @RequestParam String token,
        @RequestParam String queueId
    ) {
        QueueInfo.StatusInfo info = queueService.getStatus(token, queueId);
        return ApiResponse.success(QueueV1Dto.StatusResponse.from(info));
    }

    /**
     * SSE 기반 순번 Push 구독.
     *
     * 클라이언트가 연결을 맺으면 현재 상태를 즉시 push.
     * 이후 스케줄러가 입장 허가 시 admitted=true 이벤트를 push.
     *
     * SSE 사용 이유:
     * - 폴링(반복 HTTP): 불필요한 트래픽 발생, 입장 후 응답 지연 가능
     * - WebSocket: 양방향 필요 없음, 단방향 서버 push로 충분
     * - SSE: HTTP/1.1 호환, 단방향 push에 적합, 재연결 내장
     *
     * 레지스트리 key = userId: 스케줄러가 admitBatch 결과로 userId를 알고 있음.
     */
    @GetMapping(value = "/api/v1/queue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public SseEmitter streamStatus(@RequestParam String token, @RequestParam String queueId) {
        Long userId = queueService.getUserIdByToken(token);
        QueueInfo.StatusInfo info = queueService.getStatus(token, queueId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 레지스트리 등록 (userId 기반 — 스케줄러 notify와 동일 key)
        sseRegistry.register(String.valueOf(userId), emitter);

        // 최초 연결 시 현재 상태 즉시 push
        try {
            emitter.send(SseEmitter.event()
                .name("queue-status")
                .data(QueueV1Dto.StatusResponse.from(info)));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}

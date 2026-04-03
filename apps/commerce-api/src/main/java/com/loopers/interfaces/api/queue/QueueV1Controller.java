package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueService queueService;

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
     * 적응형 인터벌은 클라이언트가 StatusResponse의 rank를 보고 결정.
     * Jitter도 클라이언트 책임 (서버는 retry-after로 권고 가능하나 강제 불가).
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
}

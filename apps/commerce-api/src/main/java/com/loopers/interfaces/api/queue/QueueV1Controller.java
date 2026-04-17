package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/api/v1/queue/{eventId}/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> enter(
            @PathVariable String eventId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        QueueInfo info = queueFacade.enter(eventId, userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }

    @GetMapping("/api/v1/queue/{eventId}/position")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
            @PathVariable String eventId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        QueueInfo info = queueFacade.getPosition(eventId, userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }
}

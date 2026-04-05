package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueDto;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class QueueV1Controller {

    private final QueueService queueService;

    @PostMapping("/api/v1/queue/enter")
    public ApiResponse<QueueV1Dto.QueueEntryResponse> enter(
            @RequestHeader("X-Loopers-User-Id") Long userId
    ) {
        QueueDto.QueueEntryResult result = queueService.enter(userId);
        return ApiResponse.success(QueueV1Dto.QueueEntryResponse.from(result));
    }

    @GetMapping("/api/v1/queue/position")
    public ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
            @RequestHeader("X-Loopers-User-Id") Long userId
    ) {
        QueueDto.QueuePositionResult result = queueService.getPosition(userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(result));
    }
}

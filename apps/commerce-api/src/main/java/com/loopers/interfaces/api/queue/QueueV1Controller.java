package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    public ApiResponse<QueueV1Dto.EnterResponse> enter(
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        QueueInfo info = queueFacade.enterQueue(userId);
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        QueuePositionInfo info = queueFacade.getPosition(userId);
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}

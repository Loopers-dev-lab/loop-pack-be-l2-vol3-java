package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueEntryResult;
import com.loopers.domain.queue.QueuePositionResult;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class OrderQueueV1Controller {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/enter")
    public ApiResponse<OrderQueueV1Dto.EnterResponse> enter(@LoginUser Long userId) {
        QueueEntryResult result = waitingQueueService.enter(userId);
        return ApiResponse.success(OrderQueueV1Dto.EnterResponse.from(result));
    }

    @GetMapping("/position")
    public ApiResponse<OrderQueueV1Dto.PositionResponse> getPosition(@LoginUser Long userId) {
        QueuePositionResult result = waitingQueueService.getPosition(userId);
        return ApiResponse.success(OrderQueueV1Dto.PositionResponse.from(result));
    }
}

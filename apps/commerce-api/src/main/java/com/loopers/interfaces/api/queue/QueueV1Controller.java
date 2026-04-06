package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueLeaveResult;
import com.loopers.application.queue.QueuePositionResult;
import com.loopers.application.queue.QueueResult;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueService queueService;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueueEnterResponse> enter(@Valid @RequestBody QueueV1Dto.QueueEnterRequest request) {
        QueueResult result = queueService.enter(request.userId());
        return ApiResponse.success(QueueV1Dto.QueueEnterResponse.from(result));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(@RequestParam Long userId) {
        QueuePositionResult result = queueService.getPosition(userId);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(result));
    }

    @DeleteMapping("/leave")
    @Override
    public ApiResponse<QueueV1Dto.QueueLeaveResponse> leave(@RequestParam Long userId) {
        QueueLeaveResult result = queueService.leave(userId);
        return ApiResponse.success(QueueV1Dto.QueueLeaveResponse.from(result));
    }
}
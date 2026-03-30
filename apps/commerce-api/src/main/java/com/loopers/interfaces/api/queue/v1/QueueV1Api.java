package com.loopers.interfaces.api.queue.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.queue.EnterQueueUseCase;
import com.loopers.application.queue.QueuePositionResult;
import com.loopers.application.queue.ReadQueuePositionUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Api implements QueueV1ApiSpec {

    private final EnterQueueUseCase enterQueueUseCase;
    private final ReadQueuePositionUseCase readQueuePositionUseCase;

    @PostMapping("/enter")
    @Override
    public ApiResponse<Object> enterQueue(@LoginUser Long userId) {
        enterQueueUseCase.execute(userId);
        return ApiResponse.success();
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueDto.PositionResponse> getPosition(@LoginUser Long userId) {
        QueuePositionResult result = readQueuePositionUseCase.execute(userId);
        return ApiResponse.success(QueueDto.PositionResponse.from(result));
    }
}

package com.loopers.interfaces.api.queue.v1;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.queue.EnterQueueUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Api implements QueueV1ApiSpec {

    private final EnterQueueUseCase enterQueueUseCase;

    @PostMapping("/enter")
    @Override
    public ApiResponse<Object> enterQueue(@LoginUser Long userId) {
        enterQueueUseCase.execute(userId);
        return ApiResponse.success();
    }
}

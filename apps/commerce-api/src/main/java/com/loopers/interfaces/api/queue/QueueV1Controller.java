package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueApplicationService;
import com.loopers.domain.queue.QueuePollingResult;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueApplicationService queueApplicationService;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.EnterQueueResponse> enterQueue(@AuthUser AuthenticatedUser authUser) {
        QueuePosition position = queueApplicationService.enterQueue(authUser.userId());
        return ApiResponse.success(QueueV1Dto.EnterQueueResponse.from(position));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@AuthUser AuthenticatedUser authUser) {
        QueuePollingResult result = queueApplicationService.getPosition(authUser.userId());
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(result));
    }
}

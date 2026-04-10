package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.EnterResponse> enter(
        @RequestParam String userId
    ) {
        QueueInfo info = queueFacade.enter(userId);
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(
        @RequestParam String userId
    ) {
        QueuePositionInfo info = queueFacade.getPosition(userId);
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}
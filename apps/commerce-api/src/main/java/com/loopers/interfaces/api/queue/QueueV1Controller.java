package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> enter(@LoginUser UserInfo loginUser) {
        return ApiResponse.success(
                QueueV1Dto.QueuePositionResponse.from(queueFacade.enterQueue(loginUser.id()))
        );
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> position(@LoginUser UserInfo loginUser) {
        return ApiResponse.success(
                QueueV1Dto.QueuePositionResponse.from(queueFacade.getPosition(loginUser.id()))
        );
    }
}

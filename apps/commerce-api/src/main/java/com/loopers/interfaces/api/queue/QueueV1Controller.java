package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    public ApiResponse<QueueV1Dto.EnterResponse> enter(@AuthUser AuthUserPrincipal user) {
        QueueInfo info = queueFacade.enter(user.getId());
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@AuthUser AuthUserPrincipal user) {
        QueueInfo info = queueFacade.getPosition(user.getId());
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}

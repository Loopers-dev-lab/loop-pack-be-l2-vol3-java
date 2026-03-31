package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;
    private final UserFacade userFacade;

    public QueueV1Controller(QueueFacade queueFacade, UserFacade userFacade) {
        this.queueFacade = queueFacade;
        this.userFacade = userFacade;
    }

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.JoinQueueResponse> joinQueue(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        QueueInfo info = queueFacade.joinQueue(userId);
        return ApiResponse.success(QueueV1Dto.JoinQueueResponse.from(info));
    }
}


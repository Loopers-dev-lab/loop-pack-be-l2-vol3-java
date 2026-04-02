package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final QueueService queueService;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> enter(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password
    ) {
        QueuePositionInfo info = queueService.enter(loginId, password);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.QueuePositionResponse> position(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password
    ) {
        QueuePositionInfo info = queueService.getPosition(loginId, password);
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(info));
    }

    @GetMapping("/count")
    @Override
    public ApiResponse<QueueV1Dto.QueueCountResponse> count() {
        return ApiResponse.success(new QueueV1Dto.QueueCountResponse(queueService.getTotalWaitingCount()));
    }
}

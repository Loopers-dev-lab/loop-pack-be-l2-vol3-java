package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.dto.QueuePositionApiResDto;
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

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueuePositionApiResDto> enterQueue(
            @RequestHeader(HEADER_LOGIN_ID) String loginId,
            @RequestHeader(HEADER_LOGIN_PW) String password) {
        return ApiResponse.success(
                QueuePositionApiResDto.from(queueFacade.enterQueue(loginId, password))
        );
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueuePositionApiResDto> getPosition(
            @RequestHeader(HEADER_LOGIN_ID) String loginId,
            @RequestHeader(HEADER_LOGIN_PW) String password) {
        return ApiResponse.success(
                QueuePositionApiResDto.from(queueFacade.getPosition(loginId, password))
        );
    }
}

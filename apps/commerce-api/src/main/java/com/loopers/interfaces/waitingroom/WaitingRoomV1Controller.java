package com.loopers.interfaces.waitingroom;

import com.loopers.application.waitingroom.WaitingRoomFacade;
import com.loopers.application.waitingroom.dto.WaitingRoomResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.waitingroom.dto.WaitingRoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class WaitingRoomV1Controller implements WaitingRoomV1ApiSpec {

    private final WaitingRoomFacade waitingRoomFacade;

    @PostMapping("/enter")
    @Override
    public ApiResponse<WaitingRoomResponse.PositionResponse> enter(@Login LoginUser loginUser) {
        WaitingRoomResult result = waitingRoomFacade.enter(loginUser.id());
        return ApiResponse.success(WaitingRoomResponse.PositionResponse.from(result));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<WaitingRoomResponse.PositionResponse> position(@Login LoginUser loginUser) {
        WaitingRoomResult result = waitingRoomFacade.getPosition(loginUser.id());
        return ApiResponse.success(WaitingRoomResponse.PositionResponse.from(result));
    }

    @DeleteMapping("/cancel")
    @Override
    public ApiResponse<Object> cancel(@Login LoginUser loginUser) {
        waitingRoomFacade.cancel(loginUser.id());
        return ApiResponse.success();
    }
}

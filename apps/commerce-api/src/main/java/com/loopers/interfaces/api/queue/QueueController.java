package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.CurrentUser;
import com.loopers.interfaces.auth.LoginRequired;
import lombok.RequiredArgsConstructor;
import com.loopers.application.queue.QueuePositionResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class QueueController {

    private final QueueFacade queueFacade;

    @LoginRequired
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/api/v1/queue/enter")
    public ApiResponse<Void> enter(@CurrentUser AuthenticatedUser user) {
        queueFacade.enter(user.id());
        return ApiResponse.success(null);
    }

    @LoginRequired
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/api/v1/queue/position")
    public ApiResponse<Object> getPosition(@CurrentUser AuthenticatedUser user) {
        QueuePositionResult result = queueFacade.getPosition(user.id());
        Object response = switch (result) {
            case QueuePositionResult.Waiting w -> QueueDto.WaitingResponse.from(w);
            case QueuePositionResult.Entered e -> QueueDto.EnteredResponse.from(e);
        };
        return ApiResponse.success(response);
    }
}

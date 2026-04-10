package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.domain.queue.QueueMode;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/queue")
@RequiredArgsConstructor
public class QueueAdminV1Controller {

    private final QueueFacade queueFacade;

    // Command

    @PostMapping("/mode")
    public ApiResponse<Void> changeMode(@Valid @RequestBody QueueRequest.ModeChange request) {
        QueueMode mode = QueueMode.valueOf(request.mode().name());
        queueFacade.changeMode(mode);
        return ApiResponse.success();
    }
}

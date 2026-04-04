package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;


    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.EnterResponse> enter(
            @RequestBody QueueV1Dto.EnterRequest request
    ) {
        QueueInfo info = queueFacade.enter(request.userId());
        return ApiResponse.success(QueueV1Dto.EnterResponse.from(info));
    }

    @GetMapping("/position")
    @Override
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(
            @RequestParam Long userId
    ) {
        QueuePositionInfo info = queueFacade.getPosition(userId);
        return ApiResponse.success(new QueueV1Dto.PositionResponse(
                info.rank(), info.totalWaiting(),
                info.estimatedWaitSeconds(), info.token()
        ));
    }
}

package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueApp;
import com.loopers.application.queue.QueueInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueApp queueApp;

    @PostMapping("/enter")
    @Override
    public ResponseEntity<ApiResponse<EnterResponse>> enterQueue(
            @Valid @RequestBody EnterRequest request
    ) {
        QueueInfo info = queueApp.enterQueue(request.memberId());
        return ResponseEntity.ok(ApiResponse.success(EnterResponse.from(info)));
    }
}

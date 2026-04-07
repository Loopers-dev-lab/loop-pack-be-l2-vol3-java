package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueEntryResult;
import com.loopers.domain.queue.QueuePositionResult;
import com.loopers.domain.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.domain.user.User;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 API Controller
 *
 * POST /api/v1/queue/enter  — 대기열 진입
 * GET  /api/v1/queue/position — 순번 조회 (Polling)
 * DELETE /api/v1/queue/leave — 대기열 이탈
 */
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/enter")
    public ApiResponse<QueueResponse.Entry> enter(@AuthUser User user) {
        QueueEntryResult result = queueService.enter(user.getId());
        return ApiResponse.success(QueueResponse.Entry.from(result));
    }

    @GetMapping("/position")
    public ApiResponse<QueueResponse.Position> getPosition(@AuthUser User user) {
        QueuePositionResult result = queueService.getPosition(user.getId());
        return ApiResponse.success(QueueResponse.Position.from(result));
    }

    @DeleteMapping("/leave")
    public ApiResponse<Object> leave(@AuthUser User user) {
        queueService.leave(user.getId());
        return ApiResponse.success();
    }
}

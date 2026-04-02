package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueAppService;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueAppService queueAppService;

    @PostMapping("/enter")
    public ApiResponse<QueueDto.EnterResponse> enter(@LoginUser Member member) {
        long position = queueAppService.enter(member.getId());
        return ApiResponse.success(new QueueDto.EnterResponse(position));
    }

    @GetMapping("/position")
    public ApiResponse<QueueDto.PositionResponse> position(@LoginUser Member member) {
        QueueAppService.QueuePositionResult result = queueAppService.getPosition(member.getId());
        return ApiResponse.success(new QueueDto.PositionResponse(result.position(), result.estimatedWaitSeconds(), result.tokenIssued(), result.nextPollIntervalMs()));
    }

    @GetMapping("/size")
    public ApiResponse<QueueDto.SizeResponse> size() {
        return ApiResponse.success(new QueueDto.SizeResponse(queueAppService.getSize()));
    }
}

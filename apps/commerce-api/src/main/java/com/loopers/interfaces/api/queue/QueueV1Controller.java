package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.domain.member.Member;
import com.loopers.domain.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import com.loopers.interfaces.api.queue.dto.QueueV1Dto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 대기열 API 컨트롤러.
// 클라이언트가 대기열에 진입하고, 현재 대기 상태를 폴링하는 두 가지 엔드포인트를 제공한다.
//
// 클라이언트 사용 시나리오:
// 1. POST /api/v1/queue/enter → 대기열 진입, 배정된 순번 반환
// 2. GET  /api/v1/queue/position → 현재 순번/예상 대기시간 또는 입장 토큰 반환 (폴링)
// 3. 토큰을 받으면 POST /api/v1/orders로 주문 생성 (QueueTokenInterceptor가 토큰 검증)
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    // 대기열 진입 API. 유저를 대기열에 등록하고 배정된 순번(1-based)을 반환한다.
    // 이미 대기 중인 유저가 재호출해도 기존 순번을 반환한다 (멱등성 보장).
    @PostMapping("/enter")
    public ApiResponse<QueueV1Dto.EnterResponse> enter(@LoginMember Member member) {
        long position = queueFacade.enter(member.getId());
        log.info("대기열 진입 memberId={}, position={}", member.getId(), position);
        return ApiResponse.success(new QueueV1Dto.EnterResponse(position));
    }

    // 대기 상태 조회 API. 클라이언트가 주기적으로 폴링하여 현재 상태를 확인한다.
    // - 대기 중: position(순번), estimatedWaitSeconds(예상 대기시간), pollIntervalSeconds(다음 폴링 주기) 반환
    // - 입장 가능: token(입장 토큰) 반환, position=0
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@LoginMember Member member) {
        QueuePositionInfo info = queueFacade.getPosition(member.getId());
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(info));
    }
}

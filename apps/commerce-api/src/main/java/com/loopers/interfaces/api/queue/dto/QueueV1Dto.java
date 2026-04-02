package com.loopers.interfaces.api.queue.dto;

import com.loopers.domain.queue.QueuePositionInfo;

// 대기열 API의 요청/응답 DTO를 정의하는 클래스.
// record로 불변 DTO를 구성하여 직렬화 안정성을 보장한다.
public class QueueV1Dto {

    // 대기열 진입 응답. 배정된 순번(1-based)을 반환한다.
    public record EnterResponse(long position) {
    }

    // 대기 상태 조회 응답.
    // - 대기 중: position > 0, token = null, pollIntervalSeconds로 다음 폴링 주기 안내
    // - 입장 가능: position = 0, token에 입장 토큰 포함, pollIntervalSeconds = 0
    public record PositionResponse(
            long position,
            long estimatedWaitSeconds,
            String token,
            int pollIntervalSeconds
    ) {
        // Domain 레이어의 QueuePositionInfo를 Interfaces 레이어의 응답 DTO로 변환한다.
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                    info.position(), info.estimatedWaitSeconds(),
                    info.token(), info.pollIntervalSeconds());
        }
    }
}

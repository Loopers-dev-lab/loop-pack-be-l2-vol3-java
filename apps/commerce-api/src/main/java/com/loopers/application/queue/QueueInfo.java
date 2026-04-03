package com.loopers.application.queue;

/**
 * Application Layer DTO.
 * Controller ↔ Service 간 데이터 전달. API DTO와 분리.
 */
public class QueueInfo {

    /**
     * 대기열 진입 응답.
     * token: 폴링 시 사용할 UUID 토큰
     * totalSize: 전체 대기 인원 (예상 대기 시간 계산용)
     */
    public record EnterInfo(String token, long rank, long totalSize) {}

    /**
     * 폴링 응답.
     * rank: 현재 순위 (0-based)
     * totalSize: 전체 대기 인원
     * eta: 예상 대기 시간 (초) = rank / 초당 처리량
     * admitted: 입장 허가 여부 (true면 서비스 진입 가능)
     */
    public record StatusInfo(long rank, long totalSize, long etaSeconds, boolean admitted) {}
}

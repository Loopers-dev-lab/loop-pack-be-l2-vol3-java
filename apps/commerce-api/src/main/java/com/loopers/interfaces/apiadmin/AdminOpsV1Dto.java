package com.loopers.interfaces.apiadmin;

import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 운영 조회 API DTO.
 */
public class AdminOpsV1Dto {

    /**
     * Outbox 상태 응답 DTO.
     */
    @Getter
    @Builder
    public static class OutboxStatusResponse {
        private final boolean pendingExists;
    }
}

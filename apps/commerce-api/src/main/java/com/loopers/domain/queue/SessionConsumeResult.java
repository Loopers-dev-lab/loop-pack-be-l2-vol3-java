package com.loopers.domain.queue;

public enum SessionConsumeResult {
    CONSUMED,
    ALREADY_CONSUMED,
    SESSION_EXPIRED;

    /**
     * Redis Lua CAS 스크립트의 반환값을 도메인 결과로 변환.
     * CAS라는 기술 개념은 이 메서드 안에서만 존재하고, 도메인 밖으로 새지 않는다.
     */
    public static SessionConsumeResult fromLuaResult(long value) {
        return switch ((int) value) {
            case 1 -> CONSUMED;
            case 0 -> ALREADY_CONSUMED;
            default -> SESSION_EXPIRED;
        };
    }
}

package com.loopers.domain.useraction;

/**
 * 사용자 행동 로그 Repository 인터페이스.
 */
public interface UserActionLogRepository {

    /**
     * 행동 로그 저장.
     */
    UserActionLog save(UserActionLog userActionLog);
}

package com.loopers.fake;

import com.loopers.domain.useraction.ActionType;
import com.loopers.domain.useraction.UserActionLog;
import com.loopers.domain.useraction.UserActionLogRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 테스트용 Fake UserActionLogRepository.
 * Map 기반 in-memory 구현.
 */
public class FakeUserActionLogRepository implements UserActionLogRepository {

    private final Map<Long, UserActionLog> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserActionLog save(UserActionLog userActionLog) {
        Long id = idGenerator.getAndIncrement();
        UserActionLog saved = UserActionLog.reconstitute(
            id,
            userActionLog.getUserId(),
            userActionLog.getActionType(),
            userActionLog.getTargetId(),
            userActionLog.getOccurredAt(),
            userActionLog.getCreatedAt()
        );
        store.put(id, saved);
        return saved;
    }

    /**
     * 사용자 ID로 로그 조회.
     */
    public List<UserActionLog> findByUserId(Long userId) {
        return store.values().stream()
            .filter(log -> log.getUserId().equals(userId))
            .collect(Collectors.toList());
    }

    /**
     * 액션 타입으로 로그 조회.
     */
    public List<UserActionLog> findByActionType(ActionType actionType) {
        return store.values().stream()
            .filter(log -> log.getActionType() == actionType)
            .collect(Collectors.toList());
    }

    /**
     * 모든 로그 조회.
     */
    public List<UserActionLog> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 저장된 로그 수 조회.
     */
    public int size() {
        return store.size();
    }

    /**
     * 저장소 초기화.
     */
    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}

package com.loopers.fake;

import com.loopers.domain.point.UserPoint;
import com.loopers.domain.point.UserPointRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake UserPointRepository.
 */
public class FakeUserPointRepository implements UserPointRepository {

    private final Map<Long, UserPoint> storeById = new HashMap<>();
    private final Map<Long, UserPoint> storeByUserId = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserPoint save(UserPoint point) {
        Long id = point.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            point = UserPoint.reconstitute(
                id,
                point.getUserId(),
                point.getBalance(),
                point.getUpdatedAt()
            );
        }
        storeById.put(id, point);
        storeByUserId.put(point.getUserId(), point);
        return point;
    }

    @Override
    public Optional<UserPoint> findByUserId(Long userId) {
        return Optional.ofNullable(storeByUserId.get(userId));
    }

    @Override
    public Optional<UserPoint> findByUserIdWithLock(Long userId) {
        return findByUserId(userId);
    }

    public void clear() {
        storeById.clear();
        storeByUserId.clear();
        idGenerator.set(1);
    }

    public int size() {
        return storeById.size();
    }
}

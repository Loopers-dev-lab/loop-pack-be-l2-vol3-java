package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.ReconciliationMismatch;
import com.loopers.domain.payment.ReconciliationMismatchRepository;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeReconciliationMismatchRepository implements ReconciliationMismatchRepository {

    private final Map<Long, ReconciliationMismatch> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public ReconciliationMismatch save(ReconciliationMismatch mismatch) {
        if (mismatch.getId() == null || mismatch.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(mismatch, id);
        }
        setCreatedAtIfAbsent(mismatch);
        store.put(mismatch.getId(), mismatch);
        return mismatch;
    }

    @Override
    public Optional<ReconciliationMismatch> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ReconciliationMismatch> findAllByType(String type) {
        return store.values().stream()
            .filter(m -> type.equals(m.getType()))
            .toList();
    }

    @Override
    public List<ReconciliationMismatch> findAllUnresolved() {
        return store.values().stream()
            .filter(m -> m.getResolvedAt() == null)
            .toList();
    }

    private void setBaseEntityId(Object entity, long id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCreatedAtIfAbsent(ReconciliationMismatch mismatch) {
        if (mismatch.getCreatedAt() == null) {
            try {
                Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(mismatch, ZonedDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

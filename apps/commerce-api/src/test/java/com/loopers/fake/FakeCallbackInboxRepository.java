package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CallbackInbox;
import com.loopers.domain.payment.CallbackInboxRepository;
import com.loopers.domain.payment.CallbackInboxStatus;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeCallbackInboxRepository implements CallbackInboxRepository {

    private final Map<Long, CallbackInbox> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public CallbackInbox save(CallbackInbox callbackInbox) {
        if (callbackInbox.getId() == null || callbackInbox.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(callbackInbox, id);
        }
        setCreatedAtIfAbsent(callbackInbox);
        store.put(callbackInbox.getId(), callbackInbox);
        return callbackInbox;
    }

    @Override
    public Optional<CallbackInbox> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CallbackInbox> findAllByStatus(CallbackInboxStatus status) {
        return store.values().stream()
            .filter(inbox -> inbox.getStatus() == status)
            .toList();
    }

    @Override
    public List<CallbackInbox> findAllByTransactionKey(String transactionKey) {
        return store.values().stream()
            .filter(inbox -> transactionKey.equals(inbox.getTransactionKey()))
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

    private void setCreatedAtIfAbsent(CallbackInbox inbox) {
        if (inbox.getCreatedAt() == null) {
            try {
                Field createdAtField = BaseEntity.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(inbox, ZonedDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

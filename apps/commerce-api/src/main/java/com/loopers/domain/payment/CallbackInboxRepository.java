package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface CallbackInboxRepository {
    CallbackInbox save(CallbackInbox callbackInbox);
    Optional<CallbackInbox> findById(Long id);
    List<CallbackInbox> findAllByStatus(CallbackInboxStatus status);
    List<CallbackInbox> findAllByTransactionKey(String transactionKey);
}

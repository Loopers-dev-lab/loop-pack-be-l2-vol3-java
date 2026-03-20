package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CallbackInbox;
import com.loopers.domain.payment.CallbackInboxRepository;
import com.loopers.domain.payment.CallbackInboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CallbackInboxRepositoryImpl implements CallbackInboxRepository {

    private final CallbackInboxJpaRepository callbackInboxJpaRepository;

    @Override
    public CallbackInbox save(CallbackInbox callbackInbox) {
        return callbackInboxJpaRepository.save(callbackInbox);
    }

    @Override
    public Optional<CallbackInbox> findById(Long id) {
        return callbackInboxJpaRepository.findById(id);
    }

    @Override
    public List<CallbackInbox> findAllByStatus(CallbackInboxStatus status) {
        return callbackInboxJpaRepository.findAllByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public List<CallbackInbox> findAllByTransactionKey(String transactionKey) {
        return callbackInboxJpaRepository.findAllByTransactionKeyAndDeletedAtIsNull(transactionKey);
    }
}

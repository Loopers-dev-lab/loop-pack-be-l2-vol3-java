package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CallbackInbox;
import com.loopers.domain.payment.CallbackInboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallbackInboxJpaRepository extends JpaRepository<CallbackInbox, Long> {

    List<CallbackInbox> findAllByStatusAndDeletedAtIsNull(CallbackInboxStatus status);

    List<CallbackInbox> findAllByTransactionKeyAndDeletedAtIsNull(String transactionKey);
}

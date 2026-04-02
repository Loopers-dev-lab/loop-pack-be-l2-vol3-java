package com.loopers.application.event.listener;

import com.loopers.application.event.UserActionEvent;
import com.loopers.domain.useraction.UserActionLog;
import com.loopers.domain.useraction.UserActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 행동 로그 이벤트 리스너.
 * 사용자 행동 이벤트 발생 시 비동기로 로그 저장.
 * @Async로 비동기 실행되므로 메인 트랜잭션과 커넥션 경쟁 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionLogListener {

    private final UserActionLogRepository userActionLogRepository;

    /**
     * 사용자 행동 로그 저장.
     * @TransactionalEventListener와 함께 사용 시 REQUIRES_NEW 필수.
     */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleUserAction(UserActionEvent event) {
        try {
            UserActionLog actionLog = UserActionLog.create(
                event.userId(),
                event.actionType(),
                event.targetId(),
                event.occurredAt()
            );
            userActionLogRepository.save(actionLog);
            log.debug("사용자 행동 로그 저장: userId={}, actionType={}, targetId={}",
                event.userId(), event.actionType(), event.targetId());
        } catch (Exception e) {
            log.warn("사용자 행동 로그 저장 실패 (무시): userId={}, actionType={}",
                event.userId(), event.actionType(), e);
        }
    }
}
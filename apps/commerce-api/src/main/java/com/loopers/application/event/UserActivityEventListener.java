package com.loopers.application.event;

import com.loopers.domain.event.UserActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserActivityEventListener {

    @EventListener
    public void handleUserActivity(UserActivityEvent event) {
        log.info("유저 활동 기록: userId={}, activityType={}, targetId={}, targetType={}",
                event.userId(), event.activityType(), event.targetId(), event.targetType());
    }
}

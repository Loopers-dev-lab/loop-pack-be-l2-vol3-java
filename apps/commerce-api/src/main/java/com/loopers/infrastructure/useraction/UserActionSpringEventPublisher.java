package com.loopers.infrastructure.useraction;

import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.domain.useraction.UserActionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserActionSpringEventPublisher implements UserActionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(UserActionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

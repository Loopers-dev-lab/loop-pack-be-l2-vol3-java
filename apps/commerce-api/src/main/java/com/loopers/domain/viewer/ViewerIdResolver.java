package com.loopers.domain.viewer;

import org.springframework.stereotype.Component;

@Component
public class ViewerIdResolver {

    public String resolve(Long userId, String anonymousId) {
        if (userId != null) {
            return "u:" + userId;
        }
        if (anonymousId != null) {
            return "a:" + anonymousId;
        }
        throw new IllegalStateException("anonymousId는 인터셉터가 보장해야 함");
    }
}

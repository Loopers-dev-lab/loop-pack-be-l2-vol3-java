package com.loopers.domain.user.event;

import com.loopers.domain.user.UserModel;

public record UserSignedUpEvent(Long userId, String name, String email) {

    public static UserSignedUpEvent from(UserModel model) {
        return new UserSignedUpEvent(model.getId(), model.getName(), model.getEmail());
    }
}

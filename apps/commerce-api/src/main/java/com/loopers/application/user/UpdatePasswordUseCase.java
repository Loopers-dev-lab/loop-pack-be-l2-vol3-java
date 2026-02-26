package com.loopers.application.user;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 비밀번호를 변경합니다.
 *
 * <p>현재 비밀번호를 검증한 뒤 새 비밀번호로 변경합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class UpdatePasswordUseCase {

    private final UserService userService;

    /**
     * @param userId 사용자 ID
     * @param oldPassword 현재 비밀번호
     * @param newPassword 새 비밀번호
     */
    public void execute(Long userId, String oldPassword, String newPassword) {
        userService.updatePassword(userId, oldPassword, newPassword);
    }
}

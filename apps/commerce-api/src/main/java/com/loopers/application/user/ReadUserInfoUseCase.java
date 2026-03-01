package com.loopers.application.user;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 자신의 정보를 조회합니다.
 *
 * <p>사용자 ID로 조회하며, 존재하지 않는 사용자인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadUserInfoUseCase {

    private final UserRepository userRepository;

    /**
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws CoreException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public UserResult execute(Long userId) {
        return userRepository.findById(userId)
                .map(UserResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
    }
}
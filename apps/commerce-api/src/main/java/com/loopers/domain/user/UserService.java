package com.loopers.domain.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 새로운 사용자를 등록한다.
     *
     * @param loginId 로그인 ID
     * @param password 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 등록된 사용자
     * @throws CoreException 이미 존재하는 로그인 ID인 경우
     */
    @Transactional
    public User register(String loginId, String password, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(new LoginId(loginId))) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }
        try {
            User user = User.signUp(loginId, password, name, birthDate, email, passwordEncoder);
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }
    }

    /**
     * 로그인 인증을 수행한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @return 인증된 사용자 ID
     * @throws CoreException 인증 실패 시
     */
    @Transactional(readOnly = true)
    public Long login(String loginId, String loginPw) {
        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        User user = userRepository.findByLoginId(new LoginId(loginId))
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        user.verifyPassword(loginPw, passwordEncoder);

        return user.getId();
    }

    /**
     * 사용자의 비밀번호를 변경한다.
     *
     * @param userId 사용자 ID
     * @param oldPassword 현재 비밀번호
     * @param newPassword 새 비밀번호
     * @throws CoreException 사용자가 존재하지 않거나 현재 비밀번호가 일치하지 않는 경우
     */
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        user.updatePassword(oldPassword, newPassword, passwordEncoder);
    }
}

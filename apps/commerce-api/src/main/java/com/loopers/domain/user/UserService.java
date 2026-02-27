package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자(User) 도메인 서비스.
 * 회원가입, 로그인 인증, 내 정보 조회, 비밀번호 변경을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입을 수행한다. 중복 검사 → 비밀번호 검증 → 인코딩 → 엔티티 생성 → 저장 → Info 반환.
     *
     * @param command 회원가입 커맨드
     * @return 생성된 사용자 엔티티
     * @throws CoreException 로그인 ID 중복(DUPLICATE_USER_ID) 또는 비밀번호 규칙 위반(INVALID_PASSWORD)
     */
    @Transactional
    public UserModel register(UserRegisterCommand command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.DUPLICATE_USER_ID);
        }
        UserModel.validatePassword(command.rawPassword(), command.birthday());
        String encoded = passwordEncoder.encode(command.rawPassword());
        UserModel user = UserModel.createWithEncodedPassword(
                command.loginId(), encoded, command.userName(),
                command.birthday(), command.email(), command.address()
        );

        return userRepository.save(user);
    }

    /**
     * userId로 사용자를 조회한다. 존재하지 않으면 예외 발생.
     *
     * @param userId 사용자 UUID
     * @return 조회된 UserModel
     * @throws CoreException 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    public UserModel findByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
    }

    /**
     * loginId로 사용자를 조회한다. 존재하지 않으면 예외 발생.
     *
     * @param loginId 로그인 ID
     * @return 조회된 UserModel
     * @throws CoreException 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    public UserModel findByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
    }

    /**
     * 로그인 인증을 수행한다. loginId로 사용자 조회 후 비밀번호 일치 여부를 확인한다.
     *
     * @param loginId     로그인 ID
     * @param rawPassword 평문 비밀번호
     * @return 인증된 UserModel
     * @throws CoreException 사용자 미존재 또는 비밀번호 불일치 시 (UNAUTHORIZED)
     */
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 인증 후 본인 정보를 조회하여 반환한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @return 인증된 사용자 엔티티
     * @throws CoreException 인증 실패 시 (UNAUTHORIZED)
     */
    public UserModel getMyInfo(String loginId, String loginPw) {
        return authenticate(loginId, loginPw);
    }

    /**
     * 비밀번호를 변경한다. 현재 비밀번호 확인 → 동일 여부 확인 → 새 비밀번호 검증 → 인코딩 → 업데이트.
     *
     * @param loginId   로그인 ID
     * @param currentPw 현재 비밀번호
     * @param newPw     새 비밀번호
     * @throws CoreException 현재 비밀번호 불일치(PASSWORD_MISMATCH), 동일 비밀번호(SAME_PASSWORD), 규칙 위반(INVALID_PASSWORD)
     */
    @Transactional
    public void changePassword(String loginId, String currentPw, String newPw) {
        UserModel user = findByLoginId(loginId);
        if (!passwordEncoder.matches(currentPw, user.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(newPw, user.getPassword())) {
            throw new CoreException(ErrorType.SAME_PASSWORD);
        }
        UserModel.validatePassword(newPw, user.getBirthday());
        user.updatePassword(passwordEncoder.encode(newPw));
    }

    /**
     * 인증 헤더로 인증한 뒤 비밀번호를 변경한다. Controller에서 호출.
     *
     * @param loginId   인증 헤더의 로그인 ID
     * @param loginPw   인증 헤더의 비밀번호
     * @param currentPw 현재 비밀번호 (body)
     * @param newPw     새 비밀번호 (body)
     */
    @Transactional
    public void authenticateAndChangePassword(String loginId, String loginPw,
                                              String currentPw, String newPw) {
        authenticate(loginId, loginPw);
        changePassword(loginId, currentPw, newPw);
    }
}

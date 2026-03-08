package com.loopers.application.user;

import com.loopers.domain.user.*;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.exception.InvalidPasswordException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * User 애플리케이션 서비스
 * 유스케이스를 조율하고 트랜잭션을 관리합니다.
 *
 * - 트랜잭션 관리 (@Transactional)
 * - 도메인 서비스 조합
 * - 리포지토리 호출
 * - 도메인 예외 발생 (CoreException으로 변환하지 않음)
 */
@Service
@RequiredArgsConstructor
public class UserFacade {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final UserService userService;

    /**
     * 신규 사용자를 등록합니다.
     *
     * @param loginId 로그인 ID
     * @param password 평문 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @param gender 성별
     * @return 등록된 UserInfo (Application DTO)
     * @throws DuplicateLoginIdException 중복된 로그인 ID인 경우
     * @throws IllegalArgumentException 유효성 검증 실패 시
     */
    @Transactional
    public UserDto.UserInfo register(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        // 1. 중복 ID 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        // 2. 도메인 서비스를 통한 User 생성
        RawPassword rawPassword = new RawPassword(password);
        User user = userService.createUser(
            loginId,
            rawPassword,
            name,
            birthDate,
            email,
            gender,
            passwordEncryptor
        );

        // 3. 저장
        User savedUser = userRepository.save(user);

        // 4. Domain → Application DTO 변환
        return UserDto.UserInfo.from(savedUser);
    }

    /**
     * 사용자 정보를 조회합니다.
     *
     * @param id 사용자 ID
     * @return 조회된 UserInfo (Application DTO, Optional)
     */
    @Transactional(readOnly = true)
    public Optional<UserDto.UserInfo> getUserInfo(Long id) {
        return userRepository.findById(id)
            .map(UserDto.UserInfo::from);  // Domain → Application DTO 변환
    }

    /**
     * 사용자의 비밀번호를 변경합니다.
     *
     * @param userId 사용자 ID
     * @param oldPassword 기존 평문 비밀번호
     * @param newPassword 새로운 평문 비밀번호
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     * @throws InvalidPasswordException 기존 비밀번호가 일치하지 않는 경우
     * @throws IllegalArgumentException 새 비밀번호에 생년월일이 포함된 경우
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));

        // 2. 도메인 서비스를 통한 비밀번호 변경
        RawPassword rawOldPassword = new RawPassword(oldPassword);
        RawPassword rawNewPassword = new RawPassword(newPassword);

        try {
            userService.updatePassword(user, rawOldPassword, rawNewPassword, passwordEncryptor);
        } catch (InvalidPasswordException e) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }

        // 3. 저장 (변경 감지 또는 명시적 save)
        userRepository.save(user);
    }
}

package com.loopers.domain.user;

import com.loopers.domain.user.policy.PasswordPolicy;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserName;
import com.loopers.support.error.CommonErrorType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 도메인 서비스
 *
 * 회원가입, 인증, 비밀번호 변경 등 사용자 도메인 핵심 비즈니스 로직을 담당한다.
 * VO 자체 검증 → 교차 검증(PasswordPolicy) → 저장 순서로 처리한다.
 */
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    public UserService(UserRepository userRepository, PasswordEncryptor passwordEncryptor) {
        this.userRepository = userRepository;
        this.passwordEncryptor = passwordEncryptor;
    }

    @Transactional
    public User createUser(String rawLoginId, String rawPassword, String rawName, String rawBirthDate, String rawEmail) {
        // 1. VO 생성 (각 VO가 자체 규칙 검증)
        LoginId loginId = new LoginId(rawLoginId);
        Password password = Password.of(rawPassword);
        UserName name = new UserName(rawName);
        BirthDate birthDate = new BirthDate(rawBirthDate);
        Email email = new Email(rawEmail);

        // 2. 교차 검증 (비밀번호에 생년월일 포함 불가)
        PasswordPolicy.validate(rawPassword, birthDate.getValue());

        // 3. 중복 ID 검증
        if (this.userRepository.existsByLoginId(loginId.getValue())) {
            throw new CoreException(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        // 4. 비밀번호 암호화 + 엔티티 생성 + 저장
        String encodedPassword = this.passwordEncryptor.encode(rawPassword);
        User user = User.create(loginId, encodedPassword, name, birthDate, email);
        return this.userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticateUser(String rawLoginId, String rawPassword) {
        if (rawLoginId == null || rawLoginId.isBlank()) {
            throw new CoreException(UserErrorType.UNAUTHORIZED, "로그인 ID는 필수입니다.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(UserErrorType.UNAUTHORIZED, "비밀번호는 필수입니다.");
        }

        User user = this.userRepository.findByLoginId(rawLoginId)
                .orElseThrow(() -> new CoreException(UserErrorType.UNAUTHORIZED));

        if (!this.passwordEncryptor.matches(rawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.UNAUTHORIZED);
        }

        return user;
    }

    @Transactional
    public void updateUserPassword(User user, String currentRawPassword, String newRawPassword) {
        if (user == null) {
            throw new CoreException(UserErrorType.USER_NOT_FOUND, "사용자 정보가 존재하지 않습니다.");
        }
        if (currentRawPassword == null || currentRawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "현재 비밀번호는 필수입니다.");
        }
        if (newRawPassword == null || newRawPassword.isBlank()) {
            throw new CoreException(UserErrorType.INVALID_PASSWORD, "새 비밀번호는 필수입니다.");
        }

        // 현재 비밀번호 확인
        if (!this.passwordEncryptor.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.PASSWORD_MISMATCH);
        }

        // 새 비밀번호 규칙 검증
        Password.of(newRawPassword);

        // 교차 검증
        PasswordPolicy.validate(newRawPassword, user.getBirthDate().getValue());

        // 동일 비밀번호 확인
        if (this.passwordEncryptor.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.SAME_PASSWORD);
        }

        // 암호화 후 변경 및 저장 (detached 엔티티 대응)
        String newEncodedPassword = this.passwordEncryptor.encode(newRawPassword);
        user.changePassword(newEncodedPassword);
        User savedUser = this.userRepository.save(user);

        // 영속화 검증: 저장된 엔티티의 비밀번호가 정상 반영되었는지 확인
        if (!savedUser.getPassword().equals(newEncodedPassword)) {
            throw new CoreException(CommonErrorType.INTERNAL_ERROR, "비밀번호 변경이 정상적으로 반영되지 않았습니다.");
        }
    }
}

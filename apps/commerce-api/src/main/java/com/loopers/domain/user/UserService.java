package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 사용자 도메인 서비스
 *
 * 회원가입, 인증, 비밀번호 변경 등 사용자 도메인 핵심 비즈니스 로직을 담당한다.
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
        LoginId loginId = new LoginId(rawLoginId);
        Password password = Password.of(rawPassword);
        UserName name = new UserName(rawName);
        BirthDate birthDate = new BirthDate(rawBirthDate);
        Email email = new Email(rawEmail);

        // 비밀번호에 생년월일 포함 불가
        validatePasswordNotContainsBirthDate(rawPassword, birthDate.getValue());

        if (this.userRepository.existsByLoginId(loginId.getValue())) {
            throw new CoreException(UserErrorType.DUPLICATE_LOGIN_ID);
        }

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

        if (!this.passwordEncryptor.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.PASSWORD_MISMATCH);
        }

        Password.of(newRawPassword);

        BirthDate birthDate = user.getBirthDate();
        if (birthDate == null) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE, "생년월일은 필수입니다.");
        }
        validatePasswordNotContainsBirthDate(newRawPassword, birthDate.getValue());

        if (this.passwordEncryptor.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.SAME_PASSWORD);
        }

        String newEncodedPassword = this.passwordEncryptor.encode(newRawPassword);
        user.changePassword(newEncodedPassword);
        this.userRepository.save(user);
    }

    /** 비밀번호에 생년월일(YYYYMMDD, YYMMDD, MMDD) 포함 금지 */
    private void validatePasswordNotContainsBirthDate(String rawPassword, LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE, "생년월일은 필수입니다.");
        }
        String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String[] patterns = { yyyymmdd, yyyymmdd.substring(2), yyyymmdd.substring(4) };
        for (String pattern : patterns) {
            if (rawPassword.contains(pattern)) {
                throw new CoreException(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
            }
        }
    }
}

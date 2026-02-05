package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public User signup(String rawLoginId, String rawPassword, String rawName, String rawBirthDate, String rawEmail) {
        // 1. VO 생성 (각 VO가 자체 규칙 검증)
        LoginId loginId = new LoginId(rawLoginId);
        Password password = Password.of(rawPassword);
        UserName name = new UserName(rawName);
        BirthDate birthDate = new BirthDate(rawBirthDate);
        Email email = new Email(rawEmail);

        // 2. 교차 검증 (비밀번호에 생년월일 포함 불가)
        PasswordPolicy.validate(rawPassword, birthDate.getValue());

        // 3. 중복 ID 검증
        if (userRepository.existsByLoginId(loginId.getValue())) {
            throw new CoreException(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        // 4. 비밀번호 암호화 + 엔티티 생성 + 저장
        String encodedPassword = passwordEncryptor.encode(rawPassword);
        User user = User.create(loginId, encodedPassword, name, birthDate.getValue(), email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String rawLoginId, String rawPassword) {
        User user = userRepository.findByLoginId(rawLoginId)
                .orElseThrow(() -> new CoreException(UserErrorType.UNAUTHORIZED));

        if (!passwordEncryptor.matches(rawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.UNAUTHORIZED);
        }

        return user;
    }

    @Transactional
    public void changePassword(User user, String currentRawPassword, String newRawPassword) {
        // 현재 비밀번호 확인
        if (!passwordEncryptor.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.PASSWORD_MISMATCH);
        }

        // 새 비밀번호 규칙 검증
        Password.of(newRawPassword);

        // 교차 검증
        PasswordPolicy.validate(newRawPassword, user.getBirthDate());

        // 동일 비밀번호 확인
        if (passwordEncryptor.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.SAME_PASSWORD);
        }

        // 암호화 후 변경 및 저장 (detached 엔티티 대응)
        user.changePassword(passwordEncryptor.encode(newRawPassword));
        userRepository.save(user);
    }
}

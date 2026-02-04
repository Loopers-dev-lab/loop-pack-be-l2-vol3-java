package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = User.create(loginId, encodedPassword, name, birthDate.getValue(), email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String rawLoginId, String rawPassword) {
        User user = userRepository.findByLoginId(rawLoginId)
                .orElseThrow(() -> new CoreException(UserErrorType.UNAUTHORIZED));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.UNAUTHORIZED);
        }

        return user;
    }

    @Transactional
    public void changePassword(User user, String currentRawPassword, String newRawPassword) {
        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.PASSWORD_MISMATCH);
        }

        // 새 비밀번호 규칙 검증
        Password newPassword = Password.of(newRawPassword);

        // 교차 검증
        PasswordPolicy.validate(newRawPassword, user.getBirthDate());

        // 동일 비밀번호 확인
        if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(UserErrorType.SAME_PASSWORD);
        }

        // 암호화 후 변경
        user.changePassword(passwordEncoder.encode(newRawPassword));
    }
}

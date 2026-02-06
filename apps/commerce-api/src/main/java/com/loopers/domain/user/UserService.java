package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public UserModel signup(String loginId, String password, String name, String birthday, String email) {
        // 1. 비밀번호 검증 (암호화 전 raw password)
        Password.validate(password, birthday);

        // 2. 중복 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        // 3. 비밀번호 암호화
        String encryptedPassword = passwordEncoder.encode(password);

        // 4. 회원 생성 및 저장
        UserModel newUser = new UserModel(loginId, encryptedPassword, name, birthday, email);
        return userRepository.save(newUser);
    }

    // 인증
    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String password) {
        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "회원 정보가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "회원 정보가 올바르지 않습니다.");
        }

        return user;
    }

    // 비밀번호 변경
    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        // 1. 사용자 조회
        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        // 2. 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호가 현재 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 동일할 수 없습니다.");
        }

        // 4. 비밀번호 규칙 검증
        Password.validate(newPassword, user.getBirthday().toString());

        // 5. 암호화 및 저장
        String encryptedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(encryptedPassword);
        userRepository.save(user);
    }

    // 로그인 ID로 조회
    @Transactional(readOnly = true)
    public UserModel findByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }
}

package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel register(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        // 중복 ID 확인
        userRepository.findByLoginId(loginId)
            .ifPresent(user -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
            });

        // 평문 비밀번호 규칙 검증
        Password.validateRawPassword(rawPassword, birthDate);

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 유저 생성 및 저장 (암호화된 비밀번호 사용)
        UserModel userModel = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);
        return userRepository.save(userModel);
    }

    @Transactional(readOnly = true)
    public UserModel getMyInfo(String loginId, String rawPassword) {
        // 유저 조회
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        // 비밀번호 검증
        if (!passwordEncoder.matches(rawPassword, user.getPassword().getValue())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        // 유저 조회
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        // 기존 비밀번호 검증
        if (!passwordEncoder.matches(currentPassword, user.getPassword().getValue())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호가 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getPassword().getValue())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.");
        }

        // 새 비밀번호 규칙 검증
        Password.validateRawPassword(newPassword, user.getBirthDate());

        // 비밀번호 암호화 및 변경
        String newEncodedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(newEncodedPassword);
    }
}

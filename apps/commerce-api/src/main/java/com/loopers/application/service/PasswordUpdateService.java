package com.loopers.application.service;

import com.loopers.application.PasswordUpdateUseCase;
import com.loopers.domain.model.Password;
import com.loopers.domain.model.User;
import com.loopers.domain.model.UserId;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class PasswordUpdateService implements PasswordUpdateUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordUpdateService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void updatePassword(UserId userId, String currentRawPassword, String newRawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate birthday = user.getBirth().getValue();
        Password currentPassword = Password.of(currentRawPassword, birthday);
        Password newPassword = Password.of(newRawPassword, birthday);

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword.getValue(), user.getEncodedPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호가 현재 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword.getValue(), user.getEncodedPassword())) {
            throw new IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.");
        }

        // 비밀번호 암호화 후 저장
        String encodedNewPassword = passwordEncoder.encrypt(newPassword.getValue());
        User updatedUser = user.changePassword(encodedNewPassword);
        userRepository.save(updatedUser);
    }
}

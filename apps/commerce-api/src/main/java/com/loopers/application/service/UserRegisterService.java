package com.loopers.application.service;

import com.loopers.application.RegisterUseCase;
import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class UserRegisterService implements RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegisterService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(String loginId, String name, String rawPassword, LocalDate birthday, String email) {
        UserId userId = UserId.of(loginId);
        UserName userName = UserName.of(name);
        Birthday birth = Birthday.of(birthday);
        Email userEmail = Email.of(email);
        Password password = Password.of(rawPassword, birthday);
        String encodedPassword = passwordEncoder.encrypt(password.getValue());

        try {
            User user = User.register(
                    userId,
                    userName,
                    encodedPassword,
                    birth,
                    userEmail,
                    WrongPasswordCount.init(),
                    LocalDateTime.now()
            );
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("이미 사용중인 ID 입니다.", ex);
        }
    }
}

package com.loopers.application.user;

import com.loopers.application.user.PasswordUpdateUseCase;
import com.loopers.application.user.RegisterUseCase;
import com.loopers.application.user.UserQueryUseCase;
import com.loopers.domain.model.user.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class UserService implements RegisterUseCase, PasswordUpdateUseCase, UserQueryUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(RegisterCommand command) {
        UserId userId = UserId.of(command.loginId());
        UserName userName = UserName.of(command.name());
        Birthday birth = Birthday.of(command.birthday());
        Email userEmail = Email.of(command.email());
        Password password = Password.of(command.rawPassword(), command.birthday());
        String encodedPassword = passwordEncoder.encrypt(password.getValue());

        try {
            User user = User.register(
                    userId, userName, encodedPassword, birth,
                    userEmail, LocalDateTime.now()
            );
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("이미 사용중인 ID 입니다.", ex);
        }
    }

    @Override
    @Transactional
    public void updatePassword(UserId userId, String currentRawPassword, String newRawPassword) {
        User user = findUser(userId);
        User updatedUser = user.changePassword(currentRawPassword, newRawPassword, passwordEncoder);
        userRepository.save(updatedUser);
    }

    @Override
    public UserInfoResponse getUserInfo(UserId userId) {
        User user = findUser(userId);

        return new UserInfoResponse(
                user.getUserId().getValue(),
                user.getUserName().maskedValue(),
                user.getBirth().getValue(),
                user.getEmail().getValue()
        );
    }

    private User findUser(UserId userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}

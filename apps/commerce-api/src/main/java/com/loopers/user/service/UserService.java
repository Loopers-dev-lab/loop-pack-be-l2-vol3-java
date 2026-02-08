package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.GetMyInfoResponse;
import com.loopers.user.exception.DuplicateLoginIdException;
import com.loopers.user.exception.InvalidCredentialsException;
import com.loopers.user.exception.SamePasswordException;
import com.loopers.user.repository.UserRepository;
import com.loopers.user.validator.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(CreateUserRequest request) {

        if(userRepository.existsByLoginId(request.loginId())){
            throw new DuplicateLoginIdException();
        }

        //비밀번호 검증
        PasswordValidator.validate(request.password(), request.birthDate());

        //비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.loginId(),
                encodedPassword,
                request.name(),
                request.birthDate(),
                request.email()
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public GetMyInfoResponse getMyInfo(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        return GetMyInfoResponse.from(user);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // 새 비밀번호가 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new SamePasswordException();
        }

        // 새 비밀번호 규칙 검증
        PasswordValidator.validate(newPassword, user.getBirthDate());

        // 비밀번호 암호화 후 저장
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.changePassword(encodedNewPassword);
    }
}

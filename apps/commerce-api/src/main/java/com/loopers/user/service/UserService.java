package com.loopers.user.service;

import com.loopers.user.domain.User;
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
    public User createUser(String loginId, String password, String name, String birthDate, String email) {

        if(userRepository.existsByLoginId(loginId)){
            throw new IllegalArgumentException("이미 가입된 ID 입니다.");
        }

        //비밀번호 검증
        PasswordValidator.validate(password, birthDate);

        //비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(password);

        User user = new User(loginId, encodedPassword, name, birthDate, email);

        return userRepository.save(user);
    }
}

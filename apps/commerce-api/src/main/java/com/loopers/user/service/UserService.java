package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.exception.DuplicateLoginIdException;
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
}

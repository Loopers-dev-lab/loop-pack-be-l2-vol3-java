package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    public UserModel signup(SignupCommand command){
        // 1. 비밀번호 검증 (암호화 전 raw password)
        Password.validate(command.password(), command.birthday());

        // 2. 중복 체크
        Optional<UserModel> existedUser = userRepository.findByLoginId(command.loginId());
        if(existedUser.isPresent()){
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 로그인 ID입니다.");
        }

        // 3. 비밀번호 암호화
        String encryptedPassword = passwordEncoder.encode(command.password());

        // 4. 회원 생성 및 저장
        UserModel newUser = new UserModel(command.loginId(), encryptedPassword, command.name(), command.birthday(), command.email());
        return userRepository.save(newUser);
    }
}

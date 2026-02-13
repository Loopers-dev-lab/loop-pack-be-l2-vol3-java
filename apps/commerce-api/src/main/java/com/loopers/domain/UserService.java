package com.loopers.domain;

import com.loopers.infrastructure.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signup(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {

        if(userRepository.find(loginId).isPresent()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"이미 존재하는 아이디입니다.");
        }

        password.validateNotContainBirthday(birthDate);

        String encodedPasswordValue = passwordEncoder.encode(password.getValue());
        Password encryptedPassword = Password.fromEncoded(encodedPasswordValue);

        UserModel userModel = new UserModel(loginId,encryptedPassword,name,birthDate,email);

        return userRepository.save(userModel);
    }

    public UserModel getMyInfo(LoginId loginId) {
        return userRepository.find(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(LoginId loginId, Password currentPassword, Password newPassword) {
        UserModel user = userRepository.find(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(currentPassword.getValue(), user.getPassword().getValue())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 검증
        if (passwordEncoder.matches(newPassword.getValue(), user.getPassword().getValue())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }

        newPassword.validateNotContainBirthday(user.getBirthDate());

        // 새 비밀번호 암호화 및 저장
        String encodedNewPassword = passwordEncoder.encode(newPassword.getValue());
        Password encryptedNewPassword = Password.fromEncoded(encodedNewPassword);

        user.changePassword(Password.fromEncoded(user.getPassword().getValue()), encryptedNewPassword);
        userRepository.save(user);
    }
}

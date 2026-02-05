package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public UserModel signup(LoginId loginId, Password password, Name name, BirthDate birthDate, Email email) {

        if(userRepository.find(loginId).isPresent()) {
            throw new CoreException(ErrorType.BAD_REQUEST,"이미 존재하는 아이디입니다.");
        }

        UserModel userModel = new UserModel(loginId,password,name,birthDate,email);

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

        user.changePassword(currentPassword, newPassword);
        userRepository.save(user);
    }
}

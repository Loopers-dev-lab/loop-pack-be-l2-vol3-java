package com.loopers.application.user;

import com.loopers.domain.user.*;
import org.springframework.stereotype.Service;

@Service
public class UserFacade {

    private final UserService userService;

    public UserFacade(UserService userService) {
        this.userService = userService;
    }

    public UserInfo signUp(
        String userId,
        String password,
        String email,
        String birthDate,
        String genderValue
    ) {
        Email emailVO = new Email(email);
        BirthDate birthDateVO = new BirthDate(birthDate);
        Password passwordVO = Password.of(password, birthDateVO);
        Gender gender = Gender.from(genderValue);

        UserModel user = userService.signUp(userId, emailVO, birthDateVO, passwordVO, gender);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String userId) {
        return userService.getMyInfo(userId);
    }

    public void updatePassword(String userId, String currentPassword, String newPassword) {
        userService.updatePassword(userId, currentPassword, newPassword);
    }
}

package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Transactional
    public LoginId signup(String loginId, String password, String name, LocalDate birthDate, String email) {
        LoginId id = LoginId.from(loginId);

        if (userRepository.findByLoginId(id).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인ID입니다.");
        }

        validatePasswordNotContainsBirthDate(password, birthDate);

        User user = User.create(id
                         , Password.of(password, passwordEncoder)
                         , Name.from(name)
                         , BirthDate.from(birthDate)
                         , Email.from(email));

        userRepository.save(user);

        return user.loginId();
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId, String password) {
        User user = authenticate(loginId, password);
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        User user = authenticate(loginId, currentPassword);

        if (user.password().matches(newPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }

        validatePasswordNotContainsBirthDate(newPassword, user.birthDate().asLocalDate());

        user.changePassword(Password.of(newPassword, passwordEncoder));
    }

    private User authenticate(String loginId, String password) {
        LoginId id = LoginId.from(loginId);

        User user = userRepository.findByLoginId(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        if (!user.password().matches(password, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String birthDateWithDash = birthDate.toString();
        String birthDateWithoutDash = birthDateWithDash.replace("-", "");

        if (password.contains(birthDateWithDash) || password.contains(birthDateWithoutDash)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 생년월일을 포함할 수 없습니다.");
        }
    }
}

package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel createUser(String loginId, String rawPassword, LocalDate birthDate, String name, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.LOGIN_ID_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.EMAIL_ALREADY_EXISTS);
        }
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);

        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = UserModel.create(loginId, encodedPassword, birthDate, name, email);
        return userRepository.save(user);
    }

    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, ErrorMessage.User.INVALID_LOGIN_INFO));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorMessage.User.INVALID_LOGIN_INFO);
        }
        return user;
    }

    public Boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public UserModel findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.User.USER_NOT_FOUND));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserModel user = findById(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.CURRENT_PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        validatePasswordNotContainsBirthDate(newPassword, user.getBirthDate());

        String newEncodedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(newEncodedPassword);
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String birthStr = birthDate.toString().replace("-", "");
        if (password.contains(birthStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}
